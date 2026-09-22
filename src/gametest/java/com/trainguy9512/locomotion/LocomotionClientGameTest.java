package com.trainguy9512.locomotion;

import com.trainguy9512.locomotion.animation.animator.JointAnimatorDispatcher;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.MixinEnvironment;

public class LocomotionClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        // A global audit force-loads other mods' optional targets before their initialization.
        // Audit the standalone development environment; production packs exercise hooks in-game.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            context.runOnClient(client -> MixinEnvironment.getCurrentEnvironment().audit());
        }

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitTicks(20);
            context.runOnClient(client -> {
                var dispatcher = JointAnimatorDispatcher.getInstance();
                if (dispatcher.getFirstPersonPlayerDataContainer().isEmpty()
                        || dispatcher.getInterpolatedFirstPersonPlayerPose().isEmpty()) {
                    throw new AssertionError("First-person animation must tick and render in a loaded world");
                }
            });
            context.takeScreenshot("locomotion-first-person");
            assertThirdPersonWalking(context);
            context.getInput().holdKeyFor(options -> options.keyUp, 20);
            context.getInput().holdKeyFor(options -> options.keyShift, 10);
            context.getInput().pressKey(options -> options.keyJump);
            context.waitTicks(20);

            singleplayer.getServer().runCommand("give @p minecraft:arrow 64");
            singleplayer.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:shield");
            for (String item : new String[]{"diamond_sword", "bow", "crossbow", "fishing_rod", "filled_map", "shulker_box"}) {
                singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:" + item);
                context.waitTicks(20);
                context.getInput().pressMouse(0);
                context.waitTicks(10);
                context.getInput().holdMouseFor(1, 20);
                context.waitTicks(10);
                context.takeScreenshot("locomotion-" + item);
            }

            singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
            singleplayer.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:air");
            BlockPos playerPos = context.computeOnClient(client -> client.player.blockPosition());
            for (String block : new String[]{"chest", "shulker_box"}) {
                BlockPos pos = playerPos.offset(0, 0, 3);
                singleplayer.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + block);
                context.getInput().lookAt(pos);
                context.waitTicks(10);
                context.getInput().pressMouse(1);
                context.waitTicks(20);
                context.takeScreenshot("locomotion-open-" + block);
                context.runOnClient(client -> {
                    if (client.player.containerMenu == client.player.inventoryMenu) {
                        throw new AssertionError("Expected to open " + block);
                    }
                    client.player.closeContainer();
                    if (JointAnimatorDispatcher.getInstance().getCurrentlyEvaluatingBlockEntityJointAnimators().isEmpty()) {
                        throw new AssertionError("Expected a block entity animation container");
                    }
                });
                context.waitTicks(20);
            }

            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(20);
            context.takeScreenshot("locomotion-third-person");
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                LocomotionMain.CONFIG.data().firstPersonPlayer.enableRenderer = false;
            });
            context.waitTicks(10);
            context.takeScreenshot("locomotion-disabled");
            context.runOnClient(client -> {
                LocomotionMain.CONFIG.data().firstPersonPlayer.enableRenderer = true;
                client.gui.setScreen(LocomotionMain.CONFIG.getConfigScreen(FabricLoader.getInstance()::isModLoaded).apply(null));
            });
            context.waitTicks(5);
            context.takeScreenshot("locomotion-config");
            context.setScreen(() -> null);
            context.waitTicks(5);
        }
    }

    private static void assertThirdPersonWalking(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.getInput().holdKey(options -> options.keyUp);
        try {
            context.waitTicks(10);
            var startPosition = context.computeOnClient(client -> client.player.position());
            Matrix4f previous = null;
            boolean legMoved = false;
            for (int sample = 0; sample < 12; sample++) {
                context.waitTicks(2);
                Matrix4f current = context.computeOnClient(client -> {
                    var model = client.getEntityRenderDispatcher().getPlayerRenderer(client.player).getModel();
                    PoseStack stack = new PoseStack();
                    model.rightLeg.translateAndRotate(stack);
                    return new Matrix4f(stack.last().pose());
                });
                if (previous != null && !previous.equals(current, 0.01f)) {
                    legMoved = true;
                }
                previous = current;
            }
            double distance = context.computeOnClient(client -> client.player.position().distanceTo(startPosition));
            if (distance < 1.0) {
                throw new AssertionError("Walking regression test must actually move the player");
            }
            context.takeScreenshot("locomotion-third-person-walking");
            if (!legMoved) {
                throw new AssertionError("Third-person leg stayed frozen while the player walked");
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

}
