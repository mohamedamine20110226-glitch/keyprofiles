package com.keyprofiles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class KeyProfilesClient implements ClientModInitializer {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type DATA_TYPE = new TypeToken<Data>() {}.getType();
	private static final String OWN_KEY = "key.keyprofiles.switch";

	private static KeyMapping switchKey;
	private static Data data = new Data();

	public static class Data {
		public String active = "A";
		public Map<String, String> A = new LinkedHashMap<>();
		public Map<String, String> B = new LinkedHashMap<>();
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("keyprofiles", "main"));

		switchKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			OWN_KEY, InputConstants.Type.KEYSYM, InputConstants.KEY_F8, category));

		load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (switchKey.consumeClick()) {
				switchProfile(client);
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			snapshotInto(active());
			save();
			client.options.save();
		});
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("keyprofiles.json");
	}

	private static void load() {
		try {
			if (Files.exists(file())) {
				Data d = GSON.fromJson(Files.readString(file()), DATA_TYPE);
				if (d != null) {
					if (d.A == null) d.A = new LinkedHashMap<>();
					if (d.B == null) d.B = new LinkedHashMap<>();
					if (!"A".equals(d.active) && !"B".equals(d.active)) d.active = "A";
					data = d;
				}
			}
		} catch (Exception e) {
			System.err.println("[keyprofiles] could not read config: " + e);
		}
	}

	private static void save() {
		try {
			Files.writeString(file(), GSON.toJson(data));
		} catch (IOException e) {
			System.err.println("[keyprofiles] could not write config: " + e);
		}
	}

	private static Map<String, String> active() {
		return "A".equals(data.active) ? data.A : data.B;
	}

	private static Map<String, String> other() {
		return "A".equals(data.active) ? data.B : data.A;
	}

	private static void snapshotInto(Map<String, String> target) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return;
		target.clear();
		for (KeyMapping km : mc.options.keyMappings) {
			if (OWN_KEY.equals(km.getName())) continue;
			target.put(km.getName(), km.saveString());
		}
	}

	private static void switchProfile(Minecraft client) {
		snapshotInto(active());

		Map<String, String> next = other();
		if (next.isEmpty()) {
			next.putAll(active());
		}

		for (KeyMapping km : client.options.keyMappings) {
			if (OWN_KEY.equals(km.getName())) continue;
			String saved = next.get(km.getName());
			if (saved != null) {
				try {
					km.setKey(InputConstants.getKey(saved));
				} catch (Exception ignored) {
				}
			}
		}
		KeyMapping.resetMapping();

		data.active = "A".equals(data.active) ? "B" : "A";

		if (client.player != null) {
			client.player.displayClientMessage(
				Component.literal("Keybind profile: " + data.active), true);
		}
	}
}
