package dk.bearware.events;

import dk.bearware.ServerStatistics;
import dk.bearware.TTMessage;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;

public class ServerStatsHelper {
    private static final int CLIENTEVENT_CMD_SERVERSTATISTICS = 360;

    public interface OnServerStatisticsListener {
        void onServerStatistics(ServerStatistics serverStatistics);
    }

    public static void registerListener(TeamTalkEventHandler handler, OnServerStatisticsListener listener) {
        Objects.requireNonNull(handler);
        TeamTalkEventHandler.ProcessTTMessage ptm = new TeamTalkEventHandler.ProcessTTMessage(listener) {
            @Override
            void processTTMessage(TTMessage msg) {
                listener.onServerStatistics(msg.serverstatistics);
            }
        };
        Map<Integer, Vector<TeamTalkEventHandler.ProcessTTMessage>> listeners = handler.listeners;
        Vector<TeamTalkEventHandler.ProcessTTMessage> v = listeners.get(CLIENTEVENT_CMD_SERVERSTATISTICS);
        if (v == null) {
            v = new Vector<>();
            listeners.put(CLIENTEVENT_CMD_SERVERSTATISTICS, v);
        }
        v.add(ptm);
    }

    public static void unregisterListener(TeamTalkEventHandler handler, final OnServerStatisticsListener listener) {
        Map<Integer, Vector<TeamTalkEventHandler.ProcessTTMessage>> listeners = handler.listeners;
        Vector<TeamTalkEventHandler.ProcessTTMessage> v = listeners.get(CLIENTEVENT_CMD_SERVERSTATISTICS);
        if (v != null) {
            v.removeIf(o -> o.o == listener);
        }
    }
}
