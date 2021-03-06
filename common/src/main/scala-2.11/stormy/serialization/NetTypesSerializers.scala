package stormy.serialization

import com.google.common.base.Optional
import io.netty.buffer.ByteBuf
import se.sics.kompics.network.Transport
import se.sics.kompics.network.netty.serialization.Serializer
import stormy.networking.NetAddress

import scala.language.implicitConversions
import scala.pickling.Defaults._
import scala.pickling._
import scala.pickling.json._

import scala.pickling.shareNothing._

// Custom Serialization for TAddress (the case class itself is fine, but the InetSocketAddress is problematic)
object NetAddressPickler extends Pickler[NetAddress] with Unpickler[NetAddress] with pickler.PrimitivePicklers with pickler.PrimitiveArrayPicklers {

    import java.net.{InetAddress, InetSocketAddress}

    override val tag = FastTypeTag[NetAddress]

    override def pickle(picklee: NetAddress, builder: PBuilder): Unit = {
        builder.hintTag(tag) // This is always required
        builder.beginEntry(picklee)
        builder.putField("ip", { fieldBuilder =>
            fieldBuilder.hintTag(byteArrayPickler.tag)
            fieldBuilder.hintStaticallyElidedType()
            byteArrayPickler.pickle(picklee.isa.getAddress.getAddress, fieldBuilder)
        })
        builder.putField("port", { fieldBuilder =>
            fieldBuilder.hintTag(intPickler.tag)
            fieldBuilder.hintStaticallyElidedType()
            intPickler.pickle(picklee.isa.getPort, fieldBuilder)
        })
        builder.endEntry()
    }

    override def unpickle(tag: String, reader: PReader): Any = {
        val ipReader = reader.readField("ip")
        ipReader.hintStaticallyElidedType()
        val ip = byteArrayPickler.unpickleEntry(ipReader).asInstanceOf[Array[Byte]]
        val portReader = reader.readField("port")
        portReader.hintStaticallyElidedType()
        val port = intPickler.unpickleEntry(portReader).asInstanceOf[Int]
        new NetAddress(new InetSocketAddress(InetAddress.getByAddress(ip), port))
    }
}

// Custom Serialization for Transport because enum picklers don't work properly in this version
object TransportPickler extends Pickler[Transport] with Unpickler[Transport] with pickler.PrimitivePicklers {

    override val tag = FastTypeTag[Transport]

    override def pickle(picklee: Transport, builder: PBuilder): Unit = {
        builder.hintTag(tag) // This is always required
        builder.beginEntry(picklee)
        builder.putField("ordinal", { fieldBuilder =>
            fieldBuilder.hintTag(bytePickler.tag)
            fieldBuilder.hintStaticallyElidedType()
            bytePickler.pickle(picklee.ordinal().toByte, fieldBuilder)
        })
        builder.endEntry()
    }

    override def unpickle(tag: String, reader: PReader): Any = {
        val ordinalReader = reader.readField("ordinal")
        ordinalReader.hintStaticallyElidedType()
        val ordinal = bytePickler.unpickleEntry(ordinalReader).asInstanceOf[Byte].toInt

        Transport.values()(ordinal)
    }
}

// serialize all object with Scala pickling
object PickleSerializer extends Serializer {
    private val charset = "UTF-8"

    override def identifier(): Int = 100

    // register our custom picklers for use with reflection picklers
    implicit val addressPickler = NetAddressPickler
    scala.pickling.runtime.GlobalRegistry.picklerMap += (addressPickler.tag.key -> (x => addressPickler))
    scala.pickling.runtime.GlobalRegistry.unpicklerMap += (addressPickler.tag.key -> addressPickler)
    implicit val transportPickler = TransportPickler
    scala.pickling.runtime.GlobalRegistry.picklerMap += (transportPickler.tag.key -> (x => transportPickler))
    scala.pickling.runtime.GlobalRegistry.unpicklerMap += (transportPickler.tag.key -> transportPickler)

    // Register custom pickler for LUT
    implicit val nrpPickler = NodeRankPairPickler
    scala.pickling.runtime.GlobalRegistry.picklerMap += (nrpPickler.tag.key -> (x => nrpPickler))
    scala.pickling.runtime.GlobalRegistry.unpicklerMap += (nrpPickler.tag.key -> nrpPickler)


    // Register custom pickler for LUT
    implicit val lutPickler = LutPickler
    scala.pickling.runtime.GlobalRegistry.picklerMap += (lutPickler.tag.key -> (x => lutPickler))
    scala.pickling.runtime.GlobalRegistry.unpicklerMap += (lutPickler.tag.key -> lutPickler)

    override def toBinary(obj: Any, buf: ByteBuf): Unit = {
        val ser = obj.pickle
        val bytes: Array[Byte] = ser.value.getBytes(charset)
        buf.writeInt(bytes.length)
        buf.writeBytes(bytes)
    }

    override def fromBinary(buf: ByteBuf, hint: Optional[Object]): Object = {
        val len = buf.readInt()
        val bytes: Array[Byte] = Array.ofDim[Byte](len)
        buf.readBytes(bytes)
        val ser = new String(bytes, charset)
        val unpickedObject = ser.unpickle[Any]
        unpickedObject.asInstanceOf[Object]
    }

    // a nice implicit conversion between Guava's Optional and Scala's Option
    // in case anyone wants to call our serializer manually from Scala code
    implicit def optional2Option[T](o: Option[T]): Optional[T] = o match {
        case Some(x) => Optional.of(x)
        case None => Optional.absent()
    }
}