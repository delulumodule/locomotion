package com.trainguy9512.locomotion.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trainguy9512.locomotion.access.EntityRenderStateAccess;
import com.trainguy9512.locomotion.animation.data.AnimationDataContainer;
import com.trainguy9512.locomotion.animation.pose.ModelPartSpacePose;
import com.trainguy9512.locomotion.render.LocomotionWrappedRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(ModelFeatureRenderer.class)
public class MixinModelFeatureRenderer<S> {

    @WrapOperation(
            method = "prepareModel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V")
    )
    public void redirectSetupAnim(Model<S> instance, S renderState, Operation<Void> original) {
        if (renderState instanceof LocomotionWrappedRenderState<?> wrappedRenderState) {
            original.call(instance, wrappedRenderState.getInnerValue());
            Optional<AnimationDataContainer> potentialDataContainer = wrappedRenderState.getDataContainer();
            if (potentialDataContainer.isPresent()) {
                AnimationDataContainer dataContainer = potentialDataContainer.get();
                float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
                dataContainer.computePose(partialTicks);
                dataContainer.setupAnimWithAnimationPose(instance, partialTicks);
            }
        } else if (renderState instanceof EntityRenderState) {
            original.call(instance, renderState);
            Optional<ModelPartSpacePose> potentialPose = ((EntityRenderStateAccess)renderState).animationOverhaul$getInterpolatedAnimationPose();
            potentialPose.ifPresent(pose -> pose.setupAnimOnModel(instance));
        } else {
            original.call(instance, renderState);
        }
    }

}
