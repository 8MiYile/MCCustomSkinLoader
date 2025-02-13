package customskinloader.loader;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.response.MinecraftProfilePropertiesResponse;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import com.mojang.util.UUIDTypeAdapter;
import customskinloader.CustomSkinLoader;
import customskinloader.config.SkinSiteProfile;
import customskinloader.plugin.ICustomSkinLoaderPlugin;
import customskinloader.profile.ModelManager0;
import customskinloader.profile.UserProfile;
import customskinloader.utils.HttpRequestUtil;
import customskinloader.utils.TextureUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

public class MojangAPILoader implements ICustomSkinLoaderPlugin, ProfileLoader.IProfileLoader {

    @Override
    public ProfileLoader.IProfileLoader getProfileLoader() {
        return this;
    }

    @Override
    public List<IDefaultProfile> getDefaultProfiles() {
        return Lists.newArrayList(new Mojang(this));
    }

    public abstract static class DefaultProfile implements ICustomSkinLoaderPlugin.IDefaultProfile {
        protected final MojangAPILoader loader;

        public DefaultProfile(MojangAPILoader loader) {
            this.loader = loader;
        }

        @Override
        public void updateSkinSiteProfile(SkinSiteProfile ssp) {
            ssp.type = this.loader.getName();
            ssp.apiRoot = this.getAPIRoot();
            ssp.sessionRoot = this.getSessionRoot();
        }

        public abstract String getAPIRoot();

        public abstract String getSessionRoot();
    }

    public static class Mojang extends MojangAPILoader.DefaultProfile {
        public Mojang(MojangAPILoader loader) {
            super(loader);
        }

        @Override
        public String getName() {
            return "Mojang";
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public String getAPIRoot() {
            return getMojangApiRoot();
        }

        @Override
        public String getSessionRoot() {
            return getMojangSessionRoot();
        }
    }

    @Override
    public UserProfile loadProfile(SkinSiteProfile ssp, GameProfile gameProfile) {
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = getTextures(gameProfile);
        if (!map.isEmpty()) {
            CustomSkinLoader.logger.info("Default profile will be used.");
            return ModelManager0.toUserProfile(map);
        }
        String username = gameProfile.getName();
        GameProfile newGameProfile = loadGameProfileCached(ssp.apiRoot, username);
        if (newGameProfile == null) {
            CustomSkinLoader.logger.info("Profile not found.(" + username + "'s profile not found.)");
            return null;
        }
        newGameProfile = fillGameProfile(ssp.sessionRoot, newGameProfile);
        map = getTextures(newGameProfile);
        if (!map.isEmpty()) {
            gameProfile.getProperties().putAll(newGameProfile.getProperties());
            return ModelManager0.toUserProfile(map);
        }
        CustomSkinLoader.logger.info("Profile not found.(" + username + " doesn't have skin/cape.)");
        return null;
    }

    public static final Gson GSON = new GsonBuilder().registerTypeAdapter(UUID.class, new UUIDTypeAdapter()).registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create();
    private static final Map<String, GameProfile> gameProfileCache = new ConcurrentHashMap<>();

    public static GameProfile loadGameProfileCached(String apiRoot, String username) {
        return gameProfileCache.computeIfAbsent(apiRoot + " " + username, ignored -> loadGameProfile(apiRoot, username));
    }

    //Username -> UUID
    public static GameProfile loadGameProfile(String apiRoot, String username) {
        //Doc (https://minecraft.wiki/w/Mojang_API#Query_player_UUIDs_in_batch)
        HttpRequestUtil.HttpResponce responce = HttpRequestUtil.makeHttpRequest(new HttpRequestUtil.HttpRequest(apiRoot + "profiles/minecraft").setCacheTime(600).setPayload(GSON.toJson(Collections.singletonList(username))));
        if (StringUtils.isEmpty(responce.content)) {
            return null;
        }

        GameProfile[] profiles = GSON.fromJson(responce.content, GameProfile[].class);
        if (profiles.length == 0) {
            return null;
        }
        GameProfile gameProfile = profiles[0];

        if (gameProfile.getId() == null) {
            return null;
        }
        return new GameProfile(gameProfile.getId(), gameProfile.getName());
    }

    /**
     * Get Mojang UUID by username.
     *
     * @param username username to query
     * @param standard if - in UUID
     * @return UUID in Mojang API style string. Returns {@code null} if username not found in Mojang API.
     */
    public static String getMojangUuidByUsername(String username, boolean standard) {
        GameProfile profile = loadGameProfileCached(getMojangApiRoot(), username);
        if (profile == null) {
            return null;
        }
        UUID id = profile.getId();
        return standard ? id.toString() : TextureUtil.fromUUID(id);
    }

    //UUID -> Profile
    public static GameProfile fillGameProfile(String sessionRoot, GameProfile profile) {
        //Doc (https://minecraft.wiki/w/Mojang_API#Query_player's_skin_and_cape)
        HttpRequestUtil.HttpResponce responce = HttpRequestUtil.makeHttpRequest(new HttpRequestUtil.HttpRequest(sessionRoot + "session/minecraft/profile/" + TextureUtil.fromUUID(profile.getId())).setCacheTime(90));
        if (StringUtils.isEmpty(responce.content)) {
            return profile;
        }

        MinecraftProfilePropertiesResponse propertiesResponce = GSON.fromJson(responce.content, MinecraftProfilePropertiesResponse.class);
        GameProfile newGameProfile = new GameProfile(TextureUtil.AuthlibField.MINECRAFT_PROFILE_PROPERTIES_RESPONSE_ID.get(propertiesResponce), TextureUtil.AuthlibField.MINECRAFT_PROFILE_PROPERTIES_RESPONSE_NAME.get(propertiesResponce));
        newGameProfile.getProperties().putAll(TextureUtil.AuthlibField.MINECRAFT_PROFILE_PROPERTIES_RESPONSE_PROPERTIES.get(propertiesResponce));

        return newGameProfile;
    }

    public static Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures(GameProfile gameProfile) {
        if (gameProfile == null) {
            return Maps.newHashMap();
        }
        Property textureProperty = Iterables.getFirst(gameProfile.getProperties().get("textures"), null);
        if (textureProperty == null) {
            return Maps.newHashMap();
        }
        String value = TextureUtil.AuthlibField.PROPERTY_VALUE.get(textureProperty);
        if (StringUtils.isBlank(value)) {
            return Maps.newHashMap();
        }
        String json = new String(Base64.decodeBase64(value), StandardCharsets.UTF_8);
        MinecraftTexturesPayload result = GSON.fromJson(json, MinecraftTexturesPayload.class);

        if (result == null || TextureUtil.AuthlibField.MINECRAFT_TEXTURES_PAYLOAD_TEXTURES.get(result) == null) {
            return Maps.newHashMap();
        }
        return TextureUtil.AuthlibField.MINECRAFT_TEXTURES_PAYLOAD_TEXTURES.get(result);
    }

    @Override
    public boolean compare(SkinSiteProfile ssp0, SkinSiteProfile ssp1) {
        return (!StringUtils.isNoneEmpty(ssp0.apiRoot) || ssp0.apiRoot.equalsIgnoreCase(ssp1.apiRoot)) || (!StringUtils.isNoneEmpty(ssp0.sessionRoot) || ssp0.sessionRoot.equalsIgnoreCase(ssp1.sessionRoot));
    }

    @Override
    public String getName() {
        return "MojangAPI";
    }

    @Override
    public void init(SkinSiteProfile ssp) {
        //Init default api & session root for Mojang API
        if (ssp.apiRoot == null)
            ssp.apiRoot = getMojangApiRoot();
        if (ssp.sessionRoot == null)
            ssp.sessionRoot = getMojangSessionRoot();
    }

    // Prevent authlib-injector (https://github.com/yushijinhun/authlib-injector) from modifying these strings
    private static final String MOJANG_API_ROOT = "https://api{DO_NOT_MODIFY}.mojang.com/";
    private static final String MOJANG_SESSION_ROOT = "https://sessionserver{DO_NOT_MODIFY}.mojang.com/";

    public static String getMojangApiRoot() {
        return MOJANG_API_ROOT.replace("{DO_NOT_MODIFY}", "");
    }

    public static String getMojangSessionRoot() {
        return MOJANG_SESSION_ROOT.replace("{DO_NOT_MODIFY}", "");
    }
}
