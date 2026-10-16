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
        return type.getTransmissionPower(channel.mode()) + tower.antennaScore() * type.antennaAptitude;
    }
    public static float departurePower(TowerRecord tower, ChannelKey channel, int relayCount) {
        return RelayPower.departure(towerPower(tower, channel), tower.efficiency(),
                relayCount > 0 && tower.attachment() == RelayAttachment.AMPLIFIER ? 1 : relayCount);
    }
    public static RadioLinkMath.Result receiveAtTower(double distance, double power, double cap,
                                                      FrequencingType sender, TowerRecord receiver, ChannelKey channel) {
        return receiveAtTower(distance, power, cap, sender, receiver, channel, false);
    }
    public static RadioLinkMath.Result receiveAtTower(double distance, double power, double cap,
                                                      FrequencingType sender, TowerRecord receiver, ChannelKey channel, boolean dimensional) {
        FrequencingType type = SimpleRadioFrequencing.RECEIVER;
        return receive(distance, power, cap, sender, type.receptionPower + receiver.antennaScore() * type.antennaAptitude,
                type.receptionFloor, channel, dimensional);
    }
    public static RadioLinkMath.Result receiveAtReceiver(double distance, double power, double cap,
                                                         FrequencingType sender, RadioReceiver receiver, ChannelKey channel) {
        return receiveAtReceiver(distance, power, cap, sender, receiver, channel, false);
    }
    public static RadioLinkMath.Result receiveAtReceiver(double distance, double power, double cap,
                                                         FrequencingType sender, RadioReceiver receiver, ChannelKey channel, boolean dimensional) {
        return receive(distance, power, cap, sender, receiver.getPower(), receiver.frequencingType.receptionFloor, channel, dimensional);
    }
    private static RadioLinkMath.Result receive(double distance, double power, double cap, FrequencingType type,
                                                double reception, double floor, ChannelKey channel, boolean dimensional) {
        double loss = type.transmissionDiminishment;
        double travel = loss + (dimensional ? SimpleRadioLibrary.SERVER_CONFIG.frequency.dimensionalInterference
                / (channel.mode() == com.codinglitch.simpleradio.central.Frequency.Modulation.AMPLITUDE ? 2 : 1) : 0);
        return RadioLinkMath.receive(distance, power, cap, reception, floor, loss, travel,
                type.diminishmentMethod == FrequencingType.DiminishmentMethod.MULTIPLICATIVE ? RadioLinkMath.Loss.MULTIPLICATIVE
                        : RadioLinkMath.Loss.ADDITIVE, type.getDiminishThreshold(channel.mode()), base(channel));
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
                dev.paul.simpleradiorepeaters.RepeatersConfig.INSTANCE.persistentUnloadedTowers);
    }
}
