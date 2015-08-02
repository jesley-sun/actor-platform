package im.actor.model.api.rpc;
/*
 *  Generated by the Actor API Scheme generator.  DO NOT EDIT!
 */

import im.actor.model.droidkit.bser.Bser;
import im.actor.model.droidkit.bser.BserValues;
import im.actor.model.droidkit.bser.BserWriter;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import im.actor.model.network.parser.*;
import java.util.List;

public class RequestDisableInterests extends Request<ResponseVoid> {

    public static final int HEADER = 0x9e;
    public static RequestDisableInterests fromBytes(byte[] data) throws IOException {
        return Bser.parse(new RequestDisableInterests(), data);
    }

    private List<Integer> interests;

    public RequestDisableInterests(@NotNull List<Integer> interests) {
        this.interests = interests;
    }

    public RequestDisableInterests() {

    }

    @NotNull
    public List<Integer> getInterests() {
        return this.interests;
    }

    @Override
    public void parse(BserValues values) throws IOException {
        this.interests = values.getRepeatedInt(1);
    }

    @Override
    public void serialize(BserWriter writer) throws IOException {
        writer.writeRepeatedInt(1, this.interests);
    }

    @Override
    public String toString() {
        String res = "rpc DisableInterests{";
        res += "interests=" + this.interests;
        res += "}";
        return res;
    }

    @Override
    public int getHeaderKey() {
        return HEADER;
    }
}
