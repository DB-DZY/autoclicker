package com.example;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class AutoClicker implements ClientModInitializer {
	public static final String MOD_ID = "autoclicker";

	// 按键绑定
	private static KeyBinding toggleKey;
	private static boolean enabled = false;
	// 间隔控制
	private static ConfigManager config = ConfigManager.getInstance();

	private static int currentWaitTicks = 4;   // 当前使用的间隔
	private static int tickCounter = 0;
	private static final Random RANDOM = new Random();

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		// 1. 注册按键绑定
		KeyBinding.Category CATEGORY = KeyBinding.Category.create(
				Identifier.of(MOD_ID, "general")
		);
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.autoclicker.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				CATEGORY
		));

		// 2. 注册指令
		registerCommands();

		// 3. 注册 Tick 事件
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// 开关逻辑
			while (toggleKey.wasPressed()) {
				enabled = !enabled;
				if (enabled) {
					refreshCurrentInterval();
				}
				if (client.player != null) {
					client.player.sendMessage(
							Text.literal("§7[自动点击] " + (enabled ? "§a开启" : "§c关闭")),
							false
					);
				}
			}

			if (!enabled) return;
			if (client.player == null || client.world == null) return;

			tickCounter++;
			if (tickCounter < currentWaitTicks) return;
			tickCounter = 0;

			// 执行攻击
			if (client.crosshairTarget != null &&
					client.crosshairTarget.getType() == HitResult.Type.ENTITY &&
					client.interactionManager != null) {

				client.interactionManager.attackEntity(
						client.player,
						((EntityHitResult) client.crosshairTarget).getEntity()
				);
				client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);

				// 攻击后刷新下一次的随机间隔（如果随机模式开启）
				refreshCurrentInterval();
			}
		});
	}

	/**
	 * 根据当前配置刷新实际使用的间隔
	 */
	private void refreshCurrentInterval() {
		if (config.randomMode) {
			int min = Math.min(config.minIntervalTicks, config.maxIntervalTicks);
			int max = Math.max(config.minIntervalTicks, config.maxIntervalTicks);
			if (min == max) {
				currentWaitTicks = min;
			} else {
				currentWaitTicks = min + RANDOM.nextInt(max - min + 1);
			}
		} else {
			currentWaitTicks = config.minIntervalTicks;
		}
	}

	/**
	 * 注册所有客户端指令
	 */
	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			// 主指令: /autoclicker
			dispatcher.register(literal("autoclicker")
					// 显示当前设置
					.executes(context -> {
						showStatus(context.getSource().getPlayer());
						return 1;
					})

					// 设置固定间隔模式: /autoclicker fixed <ticks>
					.then(literal("fixed")
							.then(argument("ticks", IntegerArgumentType.integer(1, 200))
									.executes(context -> {
										int ticks = IntegerArgumentType.getInteger(context, "ticks");
										config.minIntervalTicks = ticks;
										config.maxIntervalTicks = ticks;
										config.randomMode = false;
										config.save();
										refreshCurrentInterval();

										sendFeedback(context.getSource().getPlayer(),
												"§a已设置为固定模式，间隔: §e" + ticks + " §a游戏刻");
										return 1;
									})))

					// 设置随机间隔模式: /autoclicker random <min> <max>
					.then(literal("random")
							.then(argument("min", IntegerArgumentType.integer(1, 200))
									.then(argument("max", IntegerArgumentType.integer(1, 200))
											.executes(context -> {
												int min = IntegerArgumentType.getInteger(context, "min");
												int max = IntegerArgumentType.getInteger(context, "max");
												if (min > max) {
													int temp = min;
													min = max;
													max = temp;
												}
												config.minIntervalTicks = min;
												config.maxIntervalTicks = max;
												config.randomMode = true;
												config.save();
												refreshCurrentInterval();

												sendFeedback(context.getSource().getPlayer(),
														"§a已设置为随机模式，范围: §e" + min + "~" + max + " §a游戏刻");
												return 1;
											}))))

					// 开关指令: /autoclicker toggle
					.then(literal("toggle")
							.executes(context -> {
								enabled = !enabled;
								sendFeedback(context.getSource().getPlayer(),
										"自动点击: " + (enabled ? "§a开启" : "§c关闭"));
								return 1;
							}))

					// 开启指令: /autoclicker on
					.then(literal("on")
							.executes(context -> {
								enabled = true;
								refreshCurrentInterval();
								sendFeedback(context.getSource().getPlayer(), "§a自动点击已开启");
								return 1;
							}))

					// 关闭指令: /autoclicker off
					.then(literal("off")
							.executes(context -> {
								enabled = false;
								sendFeedback(context.getSource().getPlayer(), "§c自动点击已关闭");
								return 1;
							}))
			);
		});
	}

	/**
	 * 显示当前状态
	 */
	private void showStatus(net.minecraft.client.network.ClientPlayerEntity player) {
		if (player == null) return;

		String mode = config.randomMode ? "§b随机模式" : "§b固定模式";
		String range;
		if (config.randomMode) {
			range = config.minIntervalTicks + " ~ " + config.maxIntervalTicks + " 刻";
		} else {
			range = config.minIntervalTicks + " 刻 (固定)";
		}
		// 动态获取按键名称
		String keyName = toggleKey.getBoundKeyLocalizedText().getString();
		if (keyName == null || keyName.isEmpty()) {
			keyName = "R";
		}

		player.sendMessage(Text.literal("§6=== 自动点击器状态 ==="), false);
		player.sendMessage(Text.literal("§7状态: " + (enabled ? "§a开启" : "§c关闭")), false);
		player.sendMessage(Text.literal("§7模式: " + mode), false);
		player.sendMessage(Text.literal("§7间隔: §e" + range), false);
		player.sendMessage(Text.literal("§7快捷键: §e"+ keyName), false);
	}

	/**
	 * 发送反馈消息（辅助方法）
	 */
	private void sendFeedback(net.minecraft.client.network.ClientPlayerEntity player, String message) {
		if (player != null) {
			player.sendMessage(Text.literal("§7[自动点击] " + message), false);
		}
	}
}