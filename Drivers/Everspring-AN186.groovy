/**
 * Everspring Z-Wave On/Off Plug - AN186
 * Author: Ryan DeShone (ardichoke)
 * Date: 2020-10-17
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 **/
metadata {
  definition(
    name: "Everspring On/Off Plug - AN186",
    namespace: "ardichoke",
    author: "Ryan DeShone",
    importUrl: "https://raw.githubusercontent.com/ardichoke/Hubitat/main/Drivers/Everspring-AN186.groovy"
  ) {
    capability "Outlet"
    capability "Switch"
    capability "Refresh"
    capability "Power Meter"
    capability "Configuration"
    capability "Polling"

    attribute "lastActivity", "String"

    fingerprint mfr: "0060", prod: "0004", deviceId: "0005", deviceJoinName: "Everspring On/Off Plug - AN186"

  }
  preferences {
    input name: "configParam3", type: "enum", title: "Remember last status", description: "Specify if the plug should remember the last status.", defaultValue: 1, options: [1: "Remember", 0: "Do not remember"], parameterSize: 1
    input name: "debugEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "infoEnable", type: "bool", title: "Enable info logging", defaultValue: true
  }
}

def parse(description) {
  def result = null
  if (debugEnable) log.debug "parse description: ${description}"
  hubitat.zwave.Command cmd = zwave.parse(description, [0x25: 2, 0x32: 5, 0x70: 2])
  if (debugEnable) log.debug "${cmd}"

  if (cmd) {
    result = zwaveEvent(cmd)
  } else {
    if (debugEnable) log.debug "Unparsed event: ${description}"
  }

  def now
  if (location.timeZone)
    now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
  else
    now = new Date().format("yyyy MMM dd EEE h:mm:ss a") sendEvent(name: "lastActivity", value: now, displayed: false)
  return result
}

def zwaveEvent(hubitat.zwave.commands.meterv5.MeterReport cmd) {
  if (debugEnable) log.debug "MeterReport ${device.label?device.label:device.name}: ${cmd}"
  def event
  if (cmd.scale == 0) {
    if (cmd.meterType == 161) {
      event = createEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V")
      if (infoEnable) log.info "${device.label?device.label:device.name}: Voltage report received with value of ${cmd.scaledMeterValue} V"
    } else if (cmd.meterType == 1) {
      event = createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
      if (infoEnable) log.info "${device.label?device.label:device.name}: Energy report received with value of ${cmd.scaledMeterValue} kWh"
    }
  } else if (cmd.scale == 1) {
    event = createEvent(name: "amperage", value: cmd.scaledMeterValue, unit: "A")
    if (infoEnable) log.info "${device.label?device.label:device.name}: Amperage report received with value of ${cmd.scaledMeterValue} A"
  } else if (cmd.scale == 2) {
    event = createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
    if (infoEnable) log.info "${device.label?device.label:device.name}: Power report received with value of ${cmd.scaledMeterValue} W"
  }

  return event
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
  if (debugEnable) log.debug "BasicSet ${device.label?device.label:device.name}: ${cmd}"
  if (infoEnable) log.info "${device.label?device.label:device.name}: Basic set received with value of ${cmd.value ? "
  on " : "
  off "}"
  return createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd) {
  if (debugEnable) log.debug "SwitchBinaryReport ${device.label?device.label:device.name}: ${cmd}"
  return switchEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
  if (debugEnable) log.debug "${device.label?device.label:device.name}: parameter '${cmd.parameterNumber}' with byte size '${cmd.size}' is set to '${cmd.configurationValue}'"
}

private switchEvents(hubitat.zwave.Command cmd, type = "physical") {
  def value = (cmd.value ? "on" : "off")
  return createEvent(name: "switch", value: value, type: type)
}

def configure() {
  if (debugEnable) log.debug "configure(): Param3=${configParam3}"
  delayBetween([
    zwave.configurationV2.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: configParam3 != null ? configParam3 : 1),
    zwave.configurationV2.configurationGet(parameterNumber: 3)
  ])
}

def on() {
  if (debugEnable) log.debug "${device.label?device.label:device.name}: on()"
  delayBetween([
    zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF).format(),
    zwave.switchBinaryV2.switchBinaryGet().format(),
    zwave.meterV5.meterReport(scale: 2).format()
  ])
}

def off() {
  if (debugEnable) log.debug "${device.label?device.label:device.name}: off()"
  delayBetween([
    zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00).format(),
    zwave.switchBinaryV2.switchBinaryGet().format(),
    zwave.meterV5.meterReport(scale: 2).format()
  ])
}

def poll() {
  if (debugEnable) log.debug "${device.label?device.label:device.name}: poll()"
  return zwave.meterV5.meterGet(scale: 2).format()
}

def refresh() {
  if (debugEnable) log.debug "${device.label?device.label:device.name}: refresh()"
  delayBetween([
    zwave.switchBinaryV2.switchBinaryGet().format(),
    zwave.meterV5.meterGet(scale: 2).format()
  ])
}
