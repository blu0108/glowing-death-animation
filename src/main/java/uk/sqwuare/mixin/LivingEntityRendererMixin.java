package uk.sqwuare.mixin;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.client.render.entity.LivingEntityRenderer.shouldFlipUpsideDown;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> implements FeatureRendererContext<T, M> {

    @Shadow protected abstract float getLyingAngle(T entity);

    protected LivingEntityRendererMixin(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Inject(method = "getOverlay", at = @At("HEAD"), cancellable = true)
    private static void redirectOverlay(LivingEntity entity, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof PlayerEntity) {
            cir.cancel();
            cir.setReturnValue(OverlayTexture.packUv(OverlayTexture.getU(whiteOverlayProgress), OverlayTexture.getV(entity.hurtTime > 0 || entity.deathTime > 0 && entity.deathTime <= 20)));
        }
    }

    @Inject(method = "setupTransforms", at = @At("HEAD"), cancellable = true)
    public void setupMathematicalTransforms(T entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale, CallbackInfo ci) {
        ci.cancel();
        applyPoseTransforms(entity, matrices, bodyYaw);
        applyDeathTransforms(entity, matrices, tickDelta);
        applyRiptideTransforms(entity, matrices, tickDelta);
        applySleepingTransforms(entity, matrices, bodyYaw);
        applyFlipUpsideDown(entity, matrices);
    }



    @Unique
    private void applyPoseTransforms(T entity, MatrixStack matrices, float bodyYaw) {
        if (!entity.isInPose(EntityPose.SLEEPING)) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
        }
    }

    @Unique
    private void applyDeathTransforms(T entity, MatrixStack matrices, float tickDelta) {
        if (entity.deathTime > 0 && entity.deathTime <= 20) {
            if (entity instanceof PlayerEntity) {
                entity.setInvisible(true);
            } else {
                float f = ((float)entity.deathTime + tickDelta - 1.0F) / 20.0F * 1.6F;
                f = MathHelper.sqrt(f);
                if (f > 1.0F) f = 1.0F;
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * this.getLyingAngle(entity)));
            }
        }
    }

    @Unique
    private void applyRiptideTransforms(T entity, MatrixStack matrices, float tickDelta) {
        if (entity.isUsingRiptide()) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F - entity.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(((float)entity.age + tickDelta) * -75.0F));
        }
    }

    @Unique
    private void applySleepingTransforms(T entity, MatrixStack matrices, float bodyYaw) {
        if (entity.isInPose(EntityPose.SLEEPING)) {
            Direction direction = entity.getSleepingDirection();
            float g = direction != null ? getYaw(direction) : bodyYaw;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(g));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(this.getLyingAngle(entity)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(270.0F));
        }
    }

    @Unique
    private void applyFlipUpsideDown(T entity, MatrixStack matrices) {
        if (shouldFlipUpsideDown(entity)) {
            matrices.translate(0.0F, entity.getHeight() + 0.1F, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        }
    }

    @Unique
    private float getYaw(Direction direction) {
        return switch (direction) {
            case SOUTH -> 90.0F;
            case NORTH -> 270.0F;
            case EAST -> 180.0F;
            default -> 0.0F;
        };
    }
}
