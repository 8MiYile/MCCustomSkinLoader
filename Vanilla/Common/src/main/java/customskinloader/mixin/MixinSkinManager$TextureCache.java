package customskinloader.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import customskinloader.fake.FakeSkinManager;
import net.minecraft.client.resources.SkinManager$TextureCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

public abstract class MixinSkinManager$TextureCache {
    // 23w31a ~ 24w45a
    @Mixin(SkinManager$TextureCache.class)
    public abstract static class V1 {
        @Shadow
        private MinecraftProfileTexture.Type type;

        @ModifyArgs(
            method = "Lnet/minecraft/client/resources/SkinManager$TextureCache;registerTexture(Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;)Ljava/util/concurrent/CompletableFuture;",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/ThreadDownloadImageData;<init>(Ljava/io/File;Ljava/lang/String;Lnet/minecraft/util/ResourceLocation;ZLjava/lang/Runnable;)V"
            )
        )
        private void modifyArgs_registerTexture(Args args, MinecraftProfileTexture profileTexture) {
            Object[] argsArr = new Object[args.size()];
            for (int i = 0; i < argsArr.length; i++) {
                argsArr[i] = args.get(i);
            }
            argsArr = FakeSkinManager.createThreadDownloadImageData(ImmutableList.copyOf(argsArr), profileTexture, this.type);
            args.setAll(argsArr);
        }
    }

    // 23w46a+
    @Mixin(SkinManager$TextureCache.class)
    public abstract static class V2 {
        @Shadow
        private MinecraftProfileTexture.Type type;

        @ModifyArgs(
            method = "Lnet/minecraft/client/resources/SkinManager$TextureCache;registerTexture(Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;)Ljava/util/concurrent/CompletableFuture;",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/texture/SkinTextureDownloader;downloadAndRegisterSkin(Lnet/minecraft/util/ResourceLocation;Ljava/nio/file/Path;Ljava/lang/String;Z)Ljava/util/concurrent/CompletableFuture;"
            )
        )
        private void modifyArgs_registerTexture(Args args, MinecraftProfileTexture profileTexture) {
            Object[] argsArr = new Object[args.size()];
            for (int i = 0; i < argsArr.length; i++) {
                argsArr[i] = args.get(i);
            }
            argsArr = FakeSkinManager.createThreadDownloadImageData(ImmutableList.copyOf(argsArr), profileTexture, this.type);
            args.setAll(argsArr);
        }
    }
}
