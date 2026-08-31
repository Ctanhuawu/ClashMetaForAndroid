package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class Proxy(
    val name: String,
    val title: String,
    val subtitle: String,
    val type: Type,
    val delay: Int,
) : Parcelable {
    object TypeSerializer : KSerializer<Type> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("com.github.kr328.clash.core.model.Proxy.Type", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Type) {
            encoder.encodeString(value.name)
        }

        override fun deserialize(decoder: Decoder): Type {
            val name = decoder.decodeString()
            if (name.equals("ninja", ignoreCase = true) || name.equals("ninjia", ignoreCase = true)) {
                return Type.Ninjia
            }
            return Type.values().firstOrNull {
                it.name.equals(name, ignoreCase = true)
            } ?: Type.Unknown
        }
    }

    @Suppress("unused")
    @Serializable(with = TypeSerializer::class)
    enum class Type(val group: Boolean) {
        Direct(false),
        Reject(false),
        RejectDrop(false),
        Compatible(false),
        Pass(false),

        Shadowsocks(false),
        ShadowsocksR(false),
        Snell(false),
        Socks5(false),
        Http(false),
        Vmess(false),
        Vless(false),
        Trojan(false),
        Hysteria(false),
        Hysteria2(false),
        Tuic(false),
        WireGuard(false),
        Dns(false),
        Ssh(false),
        Mieru(false),
        AnyTLS(false),
        Sudoku(false),
        Masque(false),
        TrustTunnel(false),
        Ninjia(false),


        Relay(true),
        Selector(true),
        Fallback(true),
        URLTest(true),
        LoadBalance(true),

        Unknown(false);
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Proxy> {
        override fun createFromParcel(parcel: Parcel): Proxy {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<Proxy?> {
            return arrayOfNulls(size)
        }
    }
}
