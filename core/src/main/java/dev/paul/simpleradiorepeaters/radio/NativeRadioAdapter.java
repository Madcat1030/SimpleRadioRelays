package dev.paul.simpleradiorepeaters.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.FrequencingType;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import dev.paul.simpleradiorepeaters.network.*;
import java.util.Objects;

public final class NativeRadioAdapter {
    private NativeRadioAdapter() {}
    public static double base(ChannelKey channel) {
        return channel.modulation().equals("AM") ? SimpleRadioLibrary.SERVER_CONFIG.frequency.baseAMInterference
                : SimpleRadioLibrary.SERVER_CONFIG.frequency.baseFMInterference;
    }
    public static float towerPower(TowerRecord tower, ChannelKey channel) {
        FrequencingType type = SimpleRadioFrequencing.TRANSMITTER;
        return (float) TowerPower.apply(type.getTransmissionPower(channel.mode()) + antennaScore(tower) * type.antennaAptitude,
                type.getTransmissionPower(channel.mode()), tower.antennaScore());
    }
    public static double antennaScore(TowerRecord tower) {
        return RelayPower.antennaScore(tower.antennaScore(), tower.attachment().antennaMultiplier())
                * dev.paul.simpleradiorepeaters.terrain.TerrainManager.elevationMultiplier(tower);
    }
    public static float departurePower(TowerRecord tower, ChannelKey channel, int relayCount) {
        return RelayPower.departure(towerPower(tower, channel), tower.efficiency(),
                relayCount > 0 && tower.attachment().resetsDecay() ? 1 : relayCount);
    }
    public static double towerReception(TowerRecord tower) {
        FrequencingType type = SimpleRadioFrequencing.RECEIVER;
        return TowerPower.apply(type.receptionPower + antennaScore(tower) * type.antennaAptitude,
                type.receptionPower, tower.antennaScore());
    }
    public static RadioLinkMath.Result receiveAtTower(double distance, double power, double cap,
                                                      FrequencingType sender, TowerRecord receiver, ChannelKey channel) {
        return receive(distance, power, cap, sender, towerReception(receiver),
                SimpleRadioFrequencing.RECEIVER.receptionFloor, channel, false, RadioPropagation.NORMAL);
    }
    public static RadioLinkMath.Result receiveAtTower(RadioSpace.Leg leg, double power, double cap,
                                                      FrequencingType sender, TowerRecord receiver, ChannelKey channel) {
        return receiveAtTower(leg, power, cap, sender, receiver, channel, 0);
    }
    public static RadioLinkMath.Result receiveAtTower(RadioSpace.Leg leg, double power, double cap,
                                                      FrequencingType sender, TowerRecord receiver, ChannelKey channel, double terrainLoss) {
        FrequencingType type = SimpleRadioFrequencing.RECEIVER;
        double reception = RelayIntegrations.receptionAtTower(
                towerReception(receiver), leg, receiver, channel);
        return receive(leg.distance() + terrainLoss, power, cap, sender, reception,
                type.receptionFloor, channel, leg.dimensional(), leg.propagation());
    }
    public static RadioLinkMath.Result receiveAtReceiver(double distance, double power, double cap,
                                                         FrequencingType sender, RadioReceiver receiver, ChannelKey channel) {
        return receive(distance, power, cap, sender, receiver.getPower(), receiver.frequencingType.receptionFloor,
                channel, false, RadioPropagation.NORMAL);
    }
    public static RadioLinkMath.Result receiveAtReceiver(RadioSpace.Leg leg, double power, double cap,
                                                         FrequencingType sender, RadioReceiver receiver, ChannelKey channel) {
        return receiveAtReceiver(leg, power, cap, sender, receiver, channel, 0);
    }
    public static RadioLinkMath.Result receiveAtReceiver(RadioSpace.Leg leg, double power, double cap,
                                                         FrequencingType sender, RadioReceiver receiver, ChannelKey channel, double terrainLoss) {
        float reception = RelayIntegrations.receptionAtReceiver(receiver.getPower(), leg, receiver, channel);
        return receive(leg.distance() + terrainLoss, power, cap, sender, reception, receiver.frequencingType.receptionFloor,
                channel, leg.dimensional(), leg.propagation());
    }
    private static RadioLinkMath.Result receive(double distance, double power, double cap, FrequencingType type,
                                                double reception, double floor, ChannelKey channel, boolean dimensional, RadioPropagation.Profile propagation) {
        double loss = type.transmissionDiminishment;
        double travel = loss + (dimensional ? SimpleRadioLibrary.SERVER_CONFIG.frequency.dimensionalInterference * propagation.dimensionalMultiplier()
                / (channel.mode() == com.codinglitch.simpleradio.central.Frequency.Modulation.AMPLITUDE ? 2 : 1) : 0);
        var received = RadioLinkMath.receive(distance, power, cap, reception, floor, loss, travel,
                type.diminishmentMethod == FrequencingType.DiminishmentMethod.MULTIPLICATIVE ? RadioLinkMath.Loss.MULTIPLICATIVE
                        : RadioLinkMath.Loss.ADDITIVE, type.getDiminishThreshold(channel.mode()), base(channel));
        return received == null ? null : new RadioLinkMath.Result(received.power(),
                Math.max(received.severity(), Math.min(100, base(channel) + propagation.noiseFloorAddition())));
    }
    public static int configurationStamp() {
        FrequencingType tx = SimpleRadioFrequencing.TRANSMITTER, rx = SimpleRadioFrequencing.RECEIVER;
        return Objects.hash(tx.transmissionPowerAM, tx.transmissionPowerFM, tx.antennaAptitude, tx.transmissionDiminishment,
                tx.diminishmentMethod, tx.diminishThresholdAM, tx.diminishThresholdFM, rx.receptionPower, rx.receptionFloor,
                rx.antennaAptitude, SimpleRadioLibrary.SERVER_CONFIG.frequency.baseAMInterference,
                SimpleRadioLibrary.SERVER_CONFIG.frequency.baseFMInterference, RelayCatalysts.configurationStamp(),
                SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional,
                SimpleRadioLibrary.SERVER_CONFIG.frequency.dimensionalInterference,
                dev.paul.simpleradiorepeaters.RepeatersConfig.INSTANCE.maxRelayHops,
                dev.paul.simpleradiorepeaters.RepeatersConfig.INSTANCE.antennaDependentBasePower,
                RadioSpace.policyFingerprint(), RelayIntegrations.configurationStamp(),
                dev.paul.simpleradiorepeaters.RepeatersConfig.INSTANCE.persistentUnloadedTowers,
                dev.paul.simpleradiorepeaters.terrain.TerrainManager.configurationStamp());
    }
}
