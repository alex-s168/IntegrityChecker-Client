package me.alex_s168.integritycheckerclient;

import net.minecraft.util.Identifier;

public class Packets {

    public static final Identifier PACKET_SERVER_REQUEST_ID =
            new Identifier("integritychecker", "server_request");

    public static final Identifier PACKET_CLIENT_USES_ICHECK_ID =
            new Identifier("integritychecker", "client_uses_icheck");

    public static final Identifier PACKET_CLIENT_SEND_ID =
            new Identifier("integritychecker", "client_send");

}
