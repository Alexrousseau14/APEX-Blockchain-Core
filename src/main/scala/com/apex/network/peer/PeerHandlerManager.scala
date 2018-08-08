package com.apex.network.peer

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.apex.common.ApexLogging
import com.apex.core.settings.ApexSettings
import com.apex.core.utils.NetworkTimeProvider

import scala.collection.mutable
import scala.util.Random
import com.apex.network.Handshake
import com.apex.network.{Incoming,Outgoing}
import com.apex.network.message.GetPeersSpec
import com.apex.network.message.Message

class PeerHandlerManager(settings: ApexSettings, timeProvider: NetworkTimeProvider) extends Actor with ApexLogging {

  import PeerHandlerManager.ReceivableMessages._
  import com.apex.network.NetworkManager.ReceivableMessages.ConnectTo
  import com.apex.network.PeerConnectionManager.ReceivableMessages.{CloseConnection, StartInteraction}
  import com.apex.network.{ConnectedPeer,ConnectionType}
  //握手成功
  private val connectedPeers = mutable.Map[InetSocketAddress, ConnectedPeer]()

  //握手前
  private val connectingPeers = mutable.Set[InetSocketAddress]()

  private lazy val peerDatabase = new PeerDatabaseImpl(Some(settings.dataDir + "/peers.dat"))

  if (peerDatabase.isEmpty()) {
    settings.network.knownPeers.foreach { address =>
      if (!isSelf(address, None)) {
        val defaultPeerInfo = PeerInfo(timeProvider.time(), None)
        peerDatabase.addOrUpdateKnownPeer(address, defaultPeerInfo)
      }
    }
  }

  private def peerListOperations: Receive = {
    case AddOrUpdatePeer(address, peerNameOpt, connTypeOpt) =>
      if (!isSelf(address, None)) {
        val peerInfo = PeerInfo(timeProvider.time(), peerNameOpt, connTypeOpt)
        peerDatabase.addOrUpdateKnownPeer(address, peerInfo)
      }
  }

  private def apiInterface: Receive = {
    case GetConnectedPeers =>
      sender() ! (connectedPeers.values.map(_.handshake).toSeq: Seq[Handshake])

    case GetAllPeers =>
      sender() ! peerDatabase.knownPeers()

    case GetBlacklistedPeers =>
      sender() ! peerDatabase.blacklistedPeers()
  }


  private def isSelf(address: InetSocketAddress, declaredAddress: Option[InetSocketAddress]): Boolean = {
    settings.network.bindAddress == address ||
      settings.network.declaredAddress.exists(da => declaredAddress.contains(da)) ||
      declaredAddress.contains(settings.network.bindAddress) ||
      settings.network.declaredAddress.contains(address)
  }

  private var lastIdUsed = 0

  private def peerCycle: Receive = connecting orElse handshaked orElse disconnected

  private def connecting: Receive = {
    case DoConnecting(remote, direction) =>
      if (peerDatabase.isBlacklisted(remote)) {
        log.info(s"从黑名单中获得传入连接 $remote")
      } else {
        val peerHandlerRef = sender
        val isIncoming = direction == Incoming
        val isAlreadyConnecting = connectingPeers.contains(remote)
        if (isAlreadyConnecting && !isIncoming) {
          log.info(s"尝试连接两次 $remote, 将删除重复连接")
          peerHandlerRef ! CloseConnection
        } else {
          if (!isIncoming) {
            log.info(s"远程链接 $remote")
            connectingPeers += remote
          }
          peerHandlerRef ! StartInteraction
          lastIdUsed += 1
        }
      }
      
    case PeerHandler(handler) =>
      log.info("连接成功后获取的PeerConnectionManager链接="+handler)
      //获取远程hangler，测发送消息
      val msg = Message[Unit](GetPeersSpec, Right(Unit), None)
      handler! msg
  }


  private def handshaked: Receive = {
    case Handshaked(peer) =>
      if (peerDatabase.isBlacklisted(peer.socketAddress)) {
        log.info(s"从黑名单中得到握手 ${peer.socketAddress}")
      } else {
        if (peer.direction == Outgoing && isSelf(peer.socketAddress, peer.handshake.declaredAddress)) {
          peer.handlerRef ! CloseConnection
        } else {
          if (peer.publicPeer) {
            self ! AddOrUpdatePeer(peer.socketAddress, Some(peer.handshake.nodeName), Some(peer.direction))
          } else {
            peerDatabase.remove(peer.socketAddress)
          }
          connectedPeers += peer.socketAddress -> peer
          log.info("更新本节点连接的节点="+connectedPeers)
        }
      }
  }

  private def disconnected: Receive = {
    case Disconnected(remote) =>
      connectedPeers -= remote
      connectingPeers -= remote
  }

  override def receive: Receive = ({
    case AddToBlacklist(peer) =>
      log.info(s"黑名单  $peer")
      peerDatabase.blacklistPeer(peer, timeProvider.time())
  }: Receive) orElse peerListOperations orElse apiInterface orElse peerCycle
}

object PeerHandlerManager {
  import com.apex.network.{ConnectedPeer,ConnectionType}
  object ReceivableMessages {
    case class AddToBlacklist(remote: InetSocketAddress)

    case class AddOrUpdatePeer(address: InetSocketAddress, peerName: Option[String], direction: Option[ConnectionType])

    case object GetConnectedPeers
    case object GetAllPeers
    case object GetBlacklistedPeers

    case class DoConnecting(remote: InetSocketAddress, direction: ConnectionType)
    case class Handshaked(peer: ConnectedPeer)
    case class PeerHandler(handlerRef: ActorRef)
    case class Disconnected(remote: InetSocketAddress)
  }
}

object PeerHandlerManagerRef {
  def props(settings: ApexSettings, timeProvider: NetworkTimeProvider): Props =
    Props(new PeerHandlerManager(settings, timeProvider))

  def apply(settings: ApexSettings, timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, timeProvider))

  def apply(name: String, settings: ApexSettings, timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, timeProvider), name)
}