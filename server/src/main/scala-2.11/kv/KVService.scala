package kv

import com.typesafe.scalalogging.StrictLogging
import networking.{NetAddress, NetMessage}
import overlay.Routing
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, PositivePort, handle}

class KVService extends ComponentDefinition with StrictLogging {
    val network: PositivePort[Network] = requires[Network]
    val route: PositivePort[Routing] = requires[Routing]

    val self: NetAddress = cfg.getValue[NetAddress]("stormy.address")

    network uponEvent {
        case context@NetMessage(source, self, op: Operation) => handle {
            logger.info(s"Got operation $op with ID ${op.id}! Now implement me please :)")
            trigger(NetMessage(self, source, OperationResponse(op.id, NotImplemented)) -> network)
        }
    }

}
