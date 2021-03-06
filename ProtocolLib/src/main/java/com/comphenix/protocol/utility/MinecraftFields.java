package com.comphenix.protocol.utility;

import org.bukkit.entity.Player;

import com.comphenix.protocol.injector.BukkitUnwrapper;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.FuzzyReflection.FieldAccessor;

/**
 * Retrieve the content of well-known fields in Minecraft.
 * @author Kristian
 */
public class MinecraftFields {
	// Cached accessors
	private static volatile FieldAccessor CONNECTION_ACCESSOR;
	private static volatile FieldAccessor NETWORK_ACCESSOR;
	
	private MinecraftFields() {
		// Not constructable
	}
	
	/**
	 * Retrieve the network mananger associated with a particular player.
	 * @param player - the player.
	 * @return The network manager.
	 */
	public static Object getNetworkManager(Player player) {
		Object nmsPlayer = BukkitUnwrapper.getInstance().unwrapItem(player);
		
		if (NETWORK_ACCESSOR == null) {
			Class<?> networkClass = MinecraftReflection.getNetworkManagerClass();
			Class<?> connectionClass = MinecraftReflection.getNetServerHandlerClass();
			NETWORK_ACCESSOR = FuzzyReflection.getFieldAccessor(connectionClass, networkClass, true);
		}
		// Retrieve the network manager
		return NETWORK_ACCESSOR.get(getPlayerConnection(nmsPlayer));
	}
	
	/**
	 * Retrieve the player connection (or NetServerHandler) associated with a player.
	 * @param player - the player.
	 * @return The player connection.
	 */
	public static Object getPlayerConnection(Player player) {
		return getPlayerConnection(BukkitUnwrapper.getInstance().unwrapItem(player));
	}
	
	// Retrieve player connection from a native instance
	private static Object getPlayerConnection(Object nmsPlayer) {
		if (CONNECTION_ACCESSOR == null) {
			Class<?> connectionClass = MinecraftReflection.getNetServerHandlerClass();
			CONNECTION_ACCESSOR = FuzzyReflection.getFieldAccessor(nmsPlayer.getClass(), connectionClass, true);
		}
		return CONNECTION_ACCESSOR.get(nmsPlayer);
	}
}
