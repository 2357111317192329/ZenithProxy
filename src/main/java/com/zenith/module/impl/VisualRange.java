package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.module.*;
import com.zenith.module.api.Module;
import com.zenith.util.ChatUtil;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.time.Instant;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class VisualRange extends Module {

    private Instant lastWhisper = Instant.EPOCH;

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ServerPlayerInVisualRangeEvent.class, this::handleNewPlayerInVisualRangeEvent),
            of(ServerPlayerLeftVisualRangeEvent.class, this::handlePlayerLeftVisualRangeEvent),
            of(ServerPlayerLogoutInVisualRangeEvent.class, this::handlePlayerLogoutInVisualRangeEvent),
            of(VisualRangeEnterEvent.class, this::enterWhisperHandler)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.visualRange.enabled;
    }

    public void handleNewPlayerInVisualRangeEvent(ServerPlayerInVisualRangeEvent event) {
        if (CONFIG.client.extra.visualRange.disablein3C3Ulobby){
            if(CACHE.getPlayerCache().getGameMode() != GameMode.SURVIVAL){
                return;
            }
        }
        var isFriend = PLAYER_LISTS.getFriendsList().contains(event.playerEntry());
        var isEnemy = PLAYER_LISTS.getEnemyList().contains(event.playerEntry());
        if (CONFIG.client.extra.visualRange.replayRecording) {
            switch (CONFIG.client.extra.visualRange.replayRecordingMode) {
                case ALL -> startReplayRecording();
                case ENEMY -> {
                    if (CONFIG.client.extra.visualRange.enemyListMode) {
                        if (isEnemy) startReplayRecording();
                    } else if (!isFriend) startReplayRecording();
                }
            }
        }
        if (CONFIG.client.extra.visualRange.enemyListMode) {
            if (!isEnemy) return;
        } else if (isFriend && CONFIG.client.extra.visualRange.ignoreFriends) {
            debug("Ignoring enter alert for friend: {}", event.playerEntry().getName());
            return;
        }
        if (CONFIG.client.extra.visualRange.enterAlert) {
            warn("{} entered visual range [{}, {}, {}]", event.playerEntry().getName(), event.playerEntity().getX(), event.playerEntity().getY(), event.playerEntity().getZ());
            EVENT_BUS.post(new VisualRangeEnterEvent(event.playerEntry(), event.playerEntity(), isFriend));
        }
    }

    public void enterWhisperHandler(VisualRangeEnterEvent event) {
        if (CONFIG.client.extra.visualRange.disablein3C3Ulobby){
            if(CACHE.getPlayerCache().getGameMode() != GameMode.SURVIVAL){
                return;
            }
        }
        if (!CONFIG.client.extra.visualRange.enterWhisper) return;
        if (!CONFIG.client.extra.visualRange.enterWhisperWhilePlayerConnected && Proxy.getInstance().hasActivePlayer()) return;
        if (Instant.now().minusSeconds(CONFIG.client.extra.visualRange.enterWhisperCooldownSeconds).isBefore(lastWhisper)) return;
        lastWhisper = Instant.now();
        sendClientPacketAsync(ChatUtil.getWhisperChatPacket(event.playerEntry().getName(), CONFIG.client.extra.visualRange.enterWhisperMessage));
    }

    public void handlePlayerLeftVisualRangeEvent(final ServerPlayerLeftVisualRangeEvent event) {
        if (CONFIG.client.extra.visualRange.disablein3C3Ulobby){
            if(CACHE.getPlayerCache().getGameMode() != GameMode.SURVIVAL){
                return;
            }
        }
        var isFriend = PLAYER_LISTS.getFriendsList().contains(event.playerEntry());
        var isEnemy = PLAYER_LISTS.getEnemyList().contains(event.playerEntry());
        if (CONFIG.client.extra.visualRange.replayRecording) {
            switch (CONFIG.client.extra.visualRange.replayRecordingMode) {
                case ALL -> {
                    if (noPlayerInVisualRange()) {
                        MODULE.get(ReplayMod.class).startDelayedRecordingStop(
                            CONFIG.client.extra.visualRange.replayRecordingCooldownMins,
                            this::noPlayerInVisualRange
                        );
                    }
                }
                case ENEMY -> {
                    if (CONFIG.client.extra.visualRange.enemyListMode) {
                        if (noEnemyListEnemyInVisualRange()) {
                            MODULE.get(ReplayMod.class).startDelayedRecordingStop(
                                CONFIG.client.extra.visualRange.replayRecordingCooldownMins,
                                this::noEnemyListEnemyInVisualRange
                            );
                        }
                    } else if (noEnemyInVisualRange()) {
                        MODULE.get(ReplayMod.class).startDelayedRecordingStop(
                            CONFIG.client.extra.visualRange.replayRecordingCooldownMins,
                            this::noEnemyInVisualRange
                        );
                    }
                }
            }
        }

        if (CONFIG.client.extra.visualRange.enemyListMode) {
            if (!isEnemy) return;
        } else if (isFriend && CONFIG.client.extra.visualRange.ignoreFriends) {
            debug("Ignoring leave alert for friend: {}", event.playerEntry().getName());
            return;
        }
        if (CONFIG.client.extra.visualRange.leaveAlert) {
            warn("{} left visual range [{}, {}, {}]", event.playerEntry().getName(), event.playerEntity().getX(), event.playerEntity().getY(), event.playerEntity().getZ());
            EVENT_BUS.post(new VisualRangeLeaveEvent(event.playerEntry(), event.playerEntity(), isFriend));
        }
    }

    private void startReplayRecording() {
        if (CONFIG.client.extra.visualRange.disablein3C3Ulobby){
            if(CACHE.getPlayerCache().getGameMode() != GameMode.SURVIVAL){
                return;
            }
        }
        if (!MODULE.get(ReplayMod.class).isEnabled()) {
            info("Starting replay recording");
            MODULE.get(ReplayMod.class).enable();
        }
    }

    private boolean noEnemyInVisualRange() {
        return CACHE.getEntityCache().getEntities().values().stream()
            .filter(entity -> entity instanceof EntityPlayer)
            .filter(entity -> !entity.equals(CACHE.getPlayerCache().getThePlayer()))
            .allMatch(entityPlayer -> PLAYER_LISTS.getFriendsList().contains((EntityPlayer) entityPlayer));
    }

    private boolean noEnemyListEnemyInVisualRange() {
        return CACHE.getEntityCache().getEntities().values().stream()
            .filter(entity -> entity instanceof EntityPlayer)
            .filter(entity -> !entity.equals(CACHE.getPlayerCache().getThePlayer()))
            .noneMatch(entityPlayer -> PLAYER_LISTS.getEnemyList().contains((EntityPlayer) entityPlayer));
    }

    private boolean noPlayerInVisualRange() {
        return CACHE.getEntityCache().getEntities().values().stream()
            .filter(entity -> entity instanceof EntityPlayer)
            .allMatch(entity -> entity.equals(CACHE.getPlayerCache().getThePlayer()));
    }

    public void handlePlayerLogoutInVisualRangeEvent(final ServerPlayerLogoutInVisualRangeEvent event) {
        if (CONFIG.client.extra.visualRange.disablein3C3Ulobby){
            if(CACHE.getPlayerCache().getGameMode() != GameMode.SURVIVAL){
                return;
            }
        }
        if (!CONFIG.client.extra.visualRange.logoutAlert) return;
        var isFriend = PLAYER_LISTS.getFriendsList().contains(event.playerEntry());
        var isEnemy = PLAYER_LISTS.getEnemyList().contains(event.playerEntry());
        if (CONFIG.client.extra.visualRange.enemyListMode) {
            if (!isEnemy) return;
        } else if (isFriend && CONFIG.client.extra.visualRange.ignoreFriends) {
            debug("Ignoring logout alert for friend: {}", event.playerEntry().getName());
            return;
        }
        warn("{} logged out in visual range [{}, {}, {}]", event.playerEntry().getName(), event.playerEntity().getX(), event.playerEntity().getY(), event.playerEntity().getZ());
        EVENT_BUS.post(new VisualRangeLogoutEvent(event.playerEntry(), event.playerEntity(), isFriend));
    }
}
