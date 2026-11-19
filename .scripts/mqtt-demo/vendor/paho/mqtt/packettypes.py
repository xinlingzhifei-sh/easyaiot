
class PacketTypes:

    """
    Packet types class.  Includes the AUTH packet for MQTT v5.0.

    Holds constants for each packet type such as PacketTypes.PUBLISH
    and packet name strings: PacketTypes.Names[PacketTypes.PUBLISH].

    """

    indexes = range(1, 16)

    # Packet types
    CONNECT, CONNACK, PUBLISH, PUBACK, PUBREC, PUBREL, \
        PUBCOMP, SUBSCRIBE, SUBACK, UNSUBSCRIBE, UNSUBACK, \
        PINGREQ, PINGRESP, DISCONNECT, AUTH = indexes

    # Dummy packet type for properties use - will delay only applies to will
    WILLMESSAGE = 99

    Names = ( "reserved", \
    "Connect", "Connack", "Publish", "Puback", "Pubrec", "Pubrel", \
    "Pubcomp", "Subscribe", "Suback", "Unsubscribe", "Unsuback", \
    "Pingreq", "Pingresp", "Disconnect", "Auth")
