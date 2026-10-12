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
    public static RadioLinkMath.Result receiveAtTower(double distance, double power, double cap,
                                                      FrequencingType sender, TowerRecord receiver, ChannelKey channel) {
        FrequencingType type = SimpleRadioFrequencing.RECEIVER;
        return receive(distance, power, cap, sender, type.receptionPower + receiver.antennaScore() * type.antennaAptitude,
                type.receptionFloor, channel);
    }
    public static RadioLinkMath.Result receiveAtReceiver(double distance, double power, double cap,
                                                         FrequencingType sender, RadioReceiver receiver, ChannelKey channel) {
        return receive(distance, power, cap, sender, receiver.getPower(), receiver.frequencingType.receptionFloor, channel);
    }
    private static RadioLinkMath.Result receive(double distance, double power, double cap, FrequencingType type,
                                                double reception, double floor, ChannelKey channel) {
        return RadioLinkMath.receive(distance, power, cap, reception, floor, type.transmissionDiminishment,
                type.diminishmentMethod == FrequencingType.DiminishmentMethod.MULTIPLICATIVE ? RadioLinkMath.Loss.MULTIPLICATIVE
                        : RadioLinkMath.Loss.ADDITIVE, type.getDiminishThreshold(channel.mode()), base(channel));
    }
    public static int configurationStamp() {
        FrequencingType tx = SimpleRadioFrequencing.TRANSMITTER, rx = SimpleRadioFrequencing.RECEIVER;
        return Objects.hash(tx.transmissionPowerAM, tx.transmissionPowerFM, tx.antennaAptitude, tx.transmissionDiminishment,
                tx.diminishmentMethod, tx.diminishThresholdAM, tx.diminishThresholdFM, rx.receptionPower, rx.receptionFloor,
                rx.antennaAptitude, SimpleRadioLibrary.SERVER_CONFIG.frequency.baseAMInterference,
                SimpleRadioLibrary.SERVER_CONFIG.frequency.baseFMInterference);
    }
}
