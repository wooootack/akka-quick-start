package com.example

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.Device.Passivate
import com.example.DeviceManager.{DeviceRegistered, ReplyDeviceList, RequestDeviceList, RequestTrackDevice}
import org.scalatest.wordspec.AnyWordSpecLike

class DeviceGroupSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Device Group" must {

    "be able to list active devices" in {
      val registeredProbe = createTestProbe[DeviceRegistered]
      val groupActor = spawn(DeviceGroup("group"))

      groupActor ! RequestTrackDevice(groupId = "group", deviceId = "device1", replyTo = registeredProbe.ref)
      registeredProbe.receiveMessage()

      groupActor ! RequestTrackDevice(groupId = "group", deviceId = "device2", replyTo = registeredProbe.ref)
      registeredProbe.receiveMessage()

      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      groupActor ! RequestDeviceList(requestId = 0, groupId = "group", replyTo = deviceListProbe.ref)
      deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device1", "device2")))
    }

    "be able to list active devices after one shuts down" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val groupActor = spawn(DeviceGroup("group"))

      groupActor ! RequestTrackDevice(groupId = "group", deviceId = "device1", replyTo = registeredProbe.ref)
      val registered1 = registeredProbe.receiveMessage()
      val toShutDown = registered1.device

      groupActor ! RequestTrackDevice(groupId =  "group", deviceId =  "device2", replyTo = registeredProbe.ref)
      registeredProbe.receiveMessage()

      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      groupActor ! RequestDeviceList(requestId = 0, groupId = "group", deviceListProbe.ref)
      deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device1", "device2")))

      toShutDown ! Passivate
      registeredProbe.expectTerminated(toShutDown, registeredProbe.remainingOrDefault)

      registeredProbe.awaitAssert {
        groupActor ! RequestDeviceList(requestId = 1, groupId = "group", replyTo = deviceListProbe.ref)
        deviceListProbe.expectMessage(ReplyDeviceList(requestId = 1, Set("device2")))
      }
    }

  }
}
