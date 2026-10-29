package org.nekit.plugin.subflipper;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.nekit.ttproplus.plugin.BasePlugin;
import org.nekit.ttproplus.plugin.PluginAction;
import org.nekit.ttproplus.plugin.PluginContext;

import java.util.concurrent.ScheduledFuture;

import dk.bearware.Subscription;
import dk.bearware.TeamTalkBase;

public class SubFlipperPlugin extends BasePlugin {

    private int targetUserId = 0;
    private long intervalMs = 300;
    private boolean isRunning = false;
    private boolean currentlySubscribed = false;
    private int flipCounter = 0;
    private ScheduledFuture<?> taskHandle = null;

    private static final int SUB_FLAGS = Subscription.SUBSCRIBE_VOICE
            | Subscription.SUBSCRIBE_MEDIAFILE
            | Subscription.SUBSCRIBE_VIDEOCAPTURE
            | Subscription.SUBSCRIBE_DESKTOP;

    @Override
    public void onLoad(PluginContext context) throws Exception {
        super.onLoad(context);
        getLogger().info("SubFlipperPlugin loaded");

        // 1. Регистрация кнопки действия в карточке плагина
        registerAction(new PluginAction() {
            @Override
            public String getId() {
                return "toggle_flipper_dialog";
            }

            @Override
            public String getTitle() {
                return isRunning ? "🛑 Остановить переключатель" : "⚡️ Переключатель подписок (Настроить)";
            }

            @Override
            public void onActionClick(Context ctx) {
                showControlDialog(ctx);
            }
        });

        // 2. Регистрация слэш-команд в чате
        registerCommand("subflip", (sender, command, args) -> {
            if (args.length == 0) {
                sender.sendMessage("Использование:\n/subflip start <userId> [интервал_мс]\n/subflip stop\n/subflip status\n/subflip interval <интервал_мс>");
                return true;
            }

            String subCmd = args[0].toLowerCase();
            switch (subCmd) {
                case "start":
                    if (args.length < 2) {
                        sender.sendMessage("Укажи User ID: /subflip start <userId> [интервал_мс]");
                        return true;
                    }
                    try {
                        int uid = Integer.parseInt(args[1]);
                        long interval = intervalMs;
                        if (args.length >= 3) {
                            interval = Math.max(50, Long.parseLong(args[2]));
                        }
                        startFlipping(uid, interval);
                        sender.sendMessage("⚡️ Флиппер запущен! Цель: #" + uid + ", интервал: " + interval + " мс");
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Ошибка: некорректный ID или интервал");
                    }
                    break;

                case "stop":
                    stopFlipping();
                    sender.sendMessage("🛑 Флиппер остановлен! Всего переключений: " + flipCounter);
                    break;

                case "status":
                    if (isRunning) {
                        sender.sendMessage("⚡️ Флиппер АКТИВЕН\nЦель: #" + targetUserId + "\nИнтервал: " + intervalMs + " мс\nСделано циклов: " + flipCounter + "\nТекущий статус: " + (currentlySubscribed ? "ВКЛ" : "ВЫКЛ"));
                    } else {
                        sender.sendMessage("🛑 Флиппер ВЫКЛЮЧЕН. Последняя цель: #" + targetUserId);
                    }
                    break;

                case "interval":
                    if (args.length < 2) {
                        sender.sendMessage("Текущий интервал: " + intervalMs + " мс. Использование: /subflip interval <мс>");
                        return true;
                    }
                    try {
                        long newInterval = Math.max(50, Long.parseLong(args[1]));
                        if (isRunning) {
                            startFlipping(targetUserId, newInterval);
                            sender.sendMessage("⚡️ Интервал изменён на " + newInterval + " мс (перезапущен)");
                        } else {
                            intervalMs = newInterval;
                            sender.sendMessage("Интервал установлен: " + newInterval + " мс");
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Ошибка: введите целое число миллисекунд");
                    }
                    break;

                default:
                    sender.sendMessage("Неизвестная подкоманда: " + subCmd + ". Доступны: start, stop, status, interval");
                    break;
            }
            return true;
        });
    }

    private synchronized void startFlipping(int uid, long interval) {
        stopFlipping();
        targetUserId = uid;
        intervalMs = interval;
        isRunning = true;
        currentlySubscribed = false;
        flipCounter = 0;

        taskHandle = getContext().schedulePeriodicTask(() -> {
            try {
                TeamTalkBase client = getClient();
                if (client == null || !isRunning || targetUserId <= 0) {
                    return;
                }

                currentlySubscribed = !currentlySubscribed;
                flipCounter++;

                if (currentlySubscribed) {
                    client.doSubscribe(targetUserId, SUB_FLAGS);
                } else {
                    client.doUnsubscribe(targetUserId, SUB_FLAGS);
                }
            } catch (Throwable t) {
                getLogger().error("Ошибка в цикле переключения подписок", t);
            }
        }, 0, intervalMs);

        getLogger().info("Флиппер запущен на пользователе #" + uid + " с интервалом " + intervalMs + " мс");
    }

    private synchronized void stopFlipping() {
        isRunning = false;
        if (taskHandle != null) {
            taskHandle.cancel(false);
            taskHandle = null;
        }

        try {
            TeamTalkBase client = getClient();
            if (client != null && targetUserId > 0) {
                client.doSubscribe(targetUserId, SUB_FLAGS);
            }
        } catch (Throwable ignored) {}

        getLogger().info("Флиппер остановлен");
    }

    private void showControlDialog(Context ctx) {
        runOnMainThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
            builder.setTitle("⚡️ Флиппер подписок TeamTalk");

            LinearLayout layout = new LinearLayout(ctx);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 30, 50, 30);

            TextView statusText = new TextView(ctx);
            statusText.setText(isRunning 
                ? "Статус: РАБОТАЕТ (Цель: #" + targetUserId + ", Циклов: " + flipCounter + ")"
                : "Статус: ОСТАНОВЛЕН");
            statusText.setPadding(0, 0, 0, 20);
            layout.addView(statusText);

            TextView labelUser = new TextView(ctx);
            labelUser.setText("ID целевого пользователя:");
            layout.addView(labelUser);

            EditText inputUser = new EditText(ctx);
            inputUser.setInputType(InputType.TYPE_CLASS_NUMBER);
            inputUser.setHint("Например: 12");
            if (targetUserId > 0) {
                inputUser.setText(String.valueOf(targetUserId));
            }
            layout.addView(inputUser);

            TextView labelInterval = new TextView(ctx);
            labelInterval.setText("Интервал переключения (мс):");
            labelInterval.setPadding(0, 20, 0, 0);
            layout.addView(labelInterval);

            EditText inputInterval = new EditText(ctx);
            inputInterval.setInputType(InputType.TYPE_CLASS_NUMBER);
            inputInterval.setText(String.valueOf(intervalMs));
            inputInterval.setHint("300");
            layout.addView(inputInterval);

            builder.setView(layout);

            if (isRunning) {
                builder.setPositiveButton("🛑 Остановить", (dialog, which) -> {
                    stopFlipping();
                    Toast.makeText(ctx, "Флиппер остановлен!", Toast.LENGTH_SHORT).show();
                });
            } else {
                builder.setPositiveButton("▶️ Запустить", (dialog, which) -> {
                    String uStr = inputUser.getText().toString().trim();
                    String iStr = inputInterval.getText().toString().trim();
                    if (uStr.isEmpty()) {
                        Toast.makeText(ctx, "Введите User ID цели!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        int uid = Integer.parseInt(uStr);
                        long interval = iStr.isEmpty() ? 300 : Math.max(50, Long.parseLong(iStr));
                        startFlipping(uid, interval);
                        Toast.makeText(ctx, "Флиппер запущен на #" + uid + " (" + interval + " мс)", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException ex) {
                        Toast.makeText(ctx, "Неверные параметры", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            builder.setNegativeButton("Закрыть", null);
            builder.show();
        });
    }

    @Override
    public void onEnable() throws Exception {
        getLogger().info("SubFlipperPlugin включен");
    }

    @Override
    public void onDisable() throws Exception {
        stopFlipping();
        getLogger().info("SubFlipperPlugin выключен");
    }

    @Override
    public void onUnload() throws Exception {
        stopFlipping();
        getLogger().info("SubFlipperPlugin выгружен");
    }
}
