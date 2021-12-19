package com.example

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.Device.{Passivate, RecordTemperature, TemperatureRecorded}
import com.example.DeviceManager.{DeviceRegistered, ReplyDeviceList, RequestAllTemperatures, RequestDeviceList, RequestTrackDevice, RespondAllTemperatures, Temperature, TemperatureNotAvailable}
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

    "be able to collect temperatures from all active devices" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val groupActor = spawn(DeviceGroup("group"))

      groupActor ! RequestTrackDevice("group", "device1", registeredProbe.ref)
      val deviceActor1 = registeredProbe.receiveMessage().device

      groupActor ! RequestTrackDevice("group", "device2", registeredProbe.ref)
      val deviceActor2 = registeredProbe.receiveMessage().device

      groupActor ! RequestTrackDevice("group", "device3", registeredProbe.ref)
      registeredProbe.receiveMessage()

      val recordProbe = createTestProbe[TemperatureRecorded]()
      deviceActor1 ! RecordTemperature(requestId = 0, value = 1.0, replyTo = recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 0))

      deviceActor2 ! RecordTemperature(requestId = 1, value = 2.0, replyTo = recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 1))

      val allTempProbe = createTestProbe[RespondAllTemperatures]()
      groupActor ! RequestAllTemperatures(requestId = 0, groupId = "group", allTempProbe.ref)
      allTempProbe.expectMessage(
        RespondAllTemperatures(
          requestId = 0,
          temperatures =
            Map("device1" -> Temperature(1.0), "device2" -> Temperature(2.0), "device3" -> TemperatureNotAvailable)
        )
      )
    }
  }
}
