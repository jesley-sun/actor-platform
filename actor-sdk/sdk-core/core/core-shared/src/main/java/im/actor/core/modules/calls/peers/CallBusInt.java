package im.actor.core.modules.calls.peers;

import im.actor.core.api.ApiWebRTCSignaling;
import im.actor.runtime.actors.ActorInterface;
import im.actor.runtime.actors.ActorRef;

public class CallBusInt extends ActorInterface {

    public CallBusInt(ActorRef dest) {
        super(dest);
    }

    public void startSlaveBus(String busId) {
        send(new CallBusActor.JoinBus(busId));
    }

    public void startMaster() {
        send(new CallBusActor.CreateBus());
    }

    public void sendSignal(long deviceId, ApiWebRTCSignaling signal) {
        send(new CallBusActor.SendSignal(deviceId, signal));
    }

    public void answerCall() {
        send(new CallBusActor.AnswerCall());
    }

    public void rejectCall() {
        send(new CallBusActor.RejectCall());
    }
}
