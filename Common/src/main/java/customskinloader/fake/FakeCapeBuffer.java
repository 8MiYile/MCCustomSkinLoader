package customskinloader.fake;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import customskinloader.CustomSkinLoader;
import customskinloader.fake.itf.FakeInterfaceManager;
import customskinloader.fake.texture.FakeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

public class FakeCapeBuffer extends FakeSkinBuffer {
    private static final ResourceLocation TEXTURE_ELYTRA_V1 = new ResourceLocation("minecraft", "textures/entity/elytra.png");
    private static final ResourceLocation TEXTURE_ELYTRA_V2 = new ResourceLocation("minecraft", "textures/entity/equipment/wings/elytra.png");
    private static int loadedGlobal = 0;
    private static FakeImage elytraImage;

    private static FakeImage loadElytra(FakeImage originalImage) {
        loadedGlobal++;
        try {
            Object resourceManager = FakeInterfaceManager.Minecraft_getResourceManager(Minecraft.getMinecraft());
            InputStream is = FakeInterfaceManager.IResource_getInputStream(FakeInterfaceManager.IResourceManager_getResource(resourceManager, TEXTURE_ELYTRA_V1)
                .orElseGet(() -> FakeInterfaceManager.IResourceManager_getResource(resourceManager, TEXTURE_ELYTRA_V2).orElse(null)));
            if (is != null) {
                FakeImage image = originalImage.createImage(is);
                if (image.getWidth() % 64 != 0 || image.getHeight() % 32 != 0) { // wtf?
                    return elytraImage;
                }
                image = resetImageFormat(image, 22, 0, 46, 22);
                return image;
            }
        } catch (IOException e) {
            CustomSkinLoader.logger.warning(e);
        }
        return null;
    }

    private int loaded = 0;
    private double ratioX = -1;
    private double ratioY = -1;
    private String type = null;

    @Override
    public FakeImage parseUserSkin(FakeImage image) {
        if (image == null) return null;
        if (isOptiFineCape(image)) {
            //OptiFine cape should be converted to standard cape
            this.image = convertOptiFineCape(image);
            //OptiFine cape contains elytra texture
            //this.type = "elytra";//But its elytra texture doesn't work
        } else {
            this.image = image;
        }

        // When resource packs were changed, the elytra image needs to be reloaded, and here will be entered again
        // The first cape which find elytra texture is outdated will reload it
        if (this.loaded == loadedGlobal) {
            elytraImage = loadElytra(this.image);
        }
        this.loaded = loadedGlobal;
        if (elytraImage != null) {
            if (this.ratioX < 0)
                this.ratioX = this.image.getWidth() / 64.0D;
            if (this.ratioY < 0)
                this.ratioY = this.image.getHeight() / 32.0D;
            if (this.type == null)
                this.type = this.judgeType();

            if ("cape".equals(this.type)) {
                this.image = resetImageFormat(this.image, 0, 0, 22, 17);
                this.attachElytra(elytraImage);
            }
        }
        return this.image;
    }

    /**
     * Judge the cape type
     *
     * @return "elytra" if the cape contains elytra texture, otherwise "cape"
     */
    @Override
    public String judgeType() {
        if (this.image != null && elytraImage != null) {
            // If all the pixels in ((22, 0), (45, 21)) is same as background, it means the cape doesn't contain elytra
            Predicate<Integer> predicate = EQU_BG.apply(this.image.getRGBA(this.image.getWidth() - 1, this.image.getHeight() - 1));
            return withElytraPixels((x, y) -> !predicate.test(this.image.getRGBA(x, y)), "elytra", "cape");
        }
        return "cape";
    }

    private void attachElytra(FakeImage elytraImage) {
        if (this.image != null) {
            int capeW = this.image.getWidth(), capeH = this.image.getHeight();
            int elytraW = elytraImage.getWidth(), elytraH = elytraImage.getHeight();

            // scale cape and elytra to the same size
            // cape part ((0, 0), (21, 16)) -> (22 * 17)
            if (capeW < elytraW) {
                this.image = scaleImage(this.image, true, elytraW / (double) capeW, 1, capeW / 64.0D, capeH / 32.0D, elytraW, capeH, 0, 0, 22, 17);
                capeW = elytraW;
            }
            if (capeH < elytraH) {
                this.image = scaleImage(this.image, true, 1, elytraH / (double) capeH, capeW / 64.0D, capeH / 32.0D, capeW, elytraH, 0, 0, 22, 17);
                capeH = elytraH;
            }
            // elytra part ((22, 0), (45, 21)) -> (24 * 22)
            if (elytraW < capeW) {
                elytraImage = scaleImage(elytraImage, false, capeW / (double) elytraW, 1, elytraW / 64.0D, elytraH / 32.0D, capeW, elytraH, 22, 0, 46, 22);
                elytraW = capeW;
            }
            if (elytraH < capeH) {
                elytraImage = scaleImage(elytraImage, false, 1, capeH / (double) elytraH, elytraW / 64.0D, elytraH / 32.0D, elytraW, capeH, 22, 0, 46, 22);
                elytraH = capeH;
            }
            this.ratioX = capeW / 64.0D;
            this.ratioY = capeH / 32.0D;

            // Overwrite pixels from elytra to cape
            FakeImage finalElytraImage = elytraImage;
            withElytraPixels((x, y) -> {
                this.image.setRGBA(x, y, finalElytraImage.getRGBA(x, y));
                return false;
            }, null, null);
        }
    }

    /**
     * Traverse every elytra pixel
     *
     * @param predicate          the predicate with x and y
     * @param returnValue        if the condition flag equals the condition, then return this value
     * @param defaultReturnValue otherwise return this value
     */
    private <R> R withElytraPixels(BiPredicate<Integer, Integer> predicate, R returnValue, R defaultReturnValue) {
        int startX = (int) Math.ceil(22 * ratioX), endX = (int) Math.ceil(46 * ratioX);
        int startY = (int) Math.ceil(0 * ratioY), endY = (int) Math.ceil(22 * ratioY);
        int excludeX0 = (int) Math.ceil(24 * ratioX), excludeX1 = (int) Math.ceil(44 * ratioX);
        int excludeY = (int) Math.ceil(2 * ratioY);
        for (int x = startX; x < endX; ++x) {
            for (int y = startY; y < endY; ++y) {
                if (y < excludeY && (x < excludeX0 || x >= excludeX1)) continue;
                if (predicate.test(x, y)) {
                    return returnValue;
                }
            }
        }
        return defaultReturnValue;
    }

    // Some cape image doesn't support alpha channel, so reset image format to ARGB
    private static FakeImage resetImageFormat(FakeImage image, int startX, int startY, int endX, int endY) {
        if (image != null) {
            int width = image.getWidth(), height = image.getHeight();
            image = scaleImage(image, true, 1, 1, width / 64.0D, height / 32.0D, width, height, startX, startY, endX, endY);
        }
        return image;
    }

    /**
     * Scale image
     *
     * @param image         the image to scale.
     * @param closeOldImage whether close old image
     * @param scaleWidth    width enlargement ratio
     * @param scaleHeight   height enlargement ratio
     * @param ratioX        the ratio of 64 of the old image width
     * @param ratioY        the ratio of 32 of the old image height
     * @param width         the width after scaling.
     * @param height        the height after scaling.
     * @param startX        the x where start to copy.
     * @param startY        the y where start to copy.
     * @param endX          the x where end to copy.
     * @param endY          the y where end to copy.
     * @return the image after scaling.
     */
    private static FakeImage scaleImage(FakeImage image, boolean closeOldImage, double scaleWidth, double scaleHeight, double ratioX, double ratioY, int width, int height, int startX, int startY, int endX, int endY) {
        FakeImage newImage = image.createImage(width, height);
        startX = (int) (startX * ratioX);
        endX = (int) (endX * ratioX);
        startY = (int) (startY * ratioY);
        endY = (int) (endY * ratioY);

        int x0 = (int) (startX * scaleWidth), x1 = (int) ((startX + 1) * scaleWidth), dx0 = x1 - x0;
        for (int x = startX; x < endX; ++x) {
            int y0 = (int) (startY * scaleHeight), y1 = (int) ((startY + 1) * scaleHeight), dy0 = y1 - y0;
            for (int y = startY; y < endY; ++y) {
                int rgba = image.getRGBA(x, y);
                for (int dx = 0; dx < dx0; dx++) {
                    for (int dy = 0; dy < dy0; dy++) {
                        newImage.setRGBA(x0 + dx, y0 + dy, rgba);
                    }
                }
                y0 = y1;
                y1 = (int) ((y + 2) * scaleHeight);
                dy0 = y1 - y0;
            }
            x0 = x1;
            x1 = (int) ((x + 2) * scaleWidth);
            dx0 = x1 - x0;
        }
        if (closeOldImage)
            image.close();
        return newImage;
    }

    /**
     * Judge OptiFine cape.
     * OptiFine cape is 46*22 or 92*44 pixels.
     * <a href="https://optifine.net/capes/Notch.png">46*22 example</a>
     * <a href="https://optifine.net/capes/OptiFineCape.png">92*44 example</a>
     *
     * @param image cape image
     * @return true if is OptiFine cape
     * @since 14.16
     */
    private static boolean isOptiFineCape(FakeImage image) {
        int ratio = image.getWidth() / 46;
        return image.getHeight() == 22 * ratio && image.getWidth() == 46 * ratio;
    }

    /**
     * Convert OptiFine cape to standard cape
     *
     * @param image OptiFine cape image
     * @return standard cape image
     * @since 14.16
     */
    private static FakeImage convertOptiFineCape(FakeImage image) {
        int ratio = image.getWidth() / 46;
        FakeImage newImage = image.createImage(64 * ratio, 32 * ratio);
        copyImageData(image, newImage);
        return newImage;
    }

    /**
     * Copy image data to another image
     *
     * @param from from image
     * @param to   to image
     */
    private static void copyImageData(FakeImage from, FakeImage to) {
        int width = Math.min(from.getWidth(), to.getWidth());
        int height = Math.min(from.getHeight(), to.getHeight());
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                to.setRGBA(x, y, from.getRGBA(x, y));
            }
        }
    }
}
