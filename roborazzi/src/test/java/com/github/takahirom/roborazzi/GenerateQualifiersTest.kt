package com.github.takahirom.roborazzi

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList

class GenerateQualifiersTest {
  /**
   * Before execute this. Please run `scripts/download_device_xml.sh`
   */
  @Test
//  @Ignore
  fun generate() {
    println(File(".").absolutePath)
    val xmlFiles =
      File("../scripts/devices").listFiles().toList().filter { it.name.endsWith(".xml") }
    println(xmlFiles)
    xmlFiles
      .forEach { xmlFile ->
        // parse xml
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val document: Document = documentBuilder.parse(xmlFile)

        val devices = document.getElementsByTagName("d:device").toList()

        devices.forEach { device ->
          // find device name
          val deviceNodes = device.childNodes.toList()
          val name = deviceNodes.first { it.nodeName == "d:name" }.textContent
            .replace(" ", "")
            .replace("(", "")
            .replace(")", "")
            .replace("'", "")
            .replace("-", "")
            .replace(".", "")
            .replace("\"", "")
            .let {
              if(it[0] in '0'..'9') "Device$it" else it
            }

          val hardwareNodes = deviceNodes.first { it.nodeName == "d:hardware" }.childNodes.toList()

          val screenNodes = hardwareNodes.first { it.nodeName == "d:screen" }.childNodes.toList()
          val screenSize = screenNodes.first { it.nodeName == "d:screen-size" }.textContent
          val screenRatio = screenNodes.first { it.nodeName == "d:screen-ratio" }.textContent
          val pixelDensity = screenNodes.first { it.nodeName == "d:pixel-density" }.textContent
          val dimensions = screenNodes.first { it.nodeName == "d:dimensions" }.childNodes.toList()
          val xDimension = dimensions.first { it.nodeName == "d:x-dimension" }.textContent.toInt()
          val yDimension = dimensions.first { it.nodeName == "d:y-dimension" }.textContent.toInt()
          val xdpi = screenNodes.first { it.nodeName == "d:xdpi" }.textContent.toDouble()
          val ydpi = screenNodes.first { it.nodeName == "d:ydpi" }.textContent.toDouble()

          val nav = hardwareNodes.first { it.nodeName == "d:nav" }.textContent

          val widthDp = (xDimension / (xdpi / 160)).toInt()
          val heightDp = (yDimension / (ydpi / 160)).toInt()

          val screenRatioQualifier = if (screenRatio == "long") "long" else "notlong"
          val shapeQualifier = if (xDimension == yDimension) "round" else "notround"

          val device = when (xmlFile.name) {
            "wear.xml" -> { "watch" }
            else -> { "any" }
          }
          if(widthDp < heightDp) {
            println ("const val ${name}Port = \"w${widthDp}dp-h${heightDp}dp-$screenSize-$screenRatioQualifier-$shapeQualifier-port-$device-$pixelDensity-keyshidden-$nav\"")
            println ("const val ${name}Land = \"w${widthDp}dp-h${heightDp}dp-$screenSize-$screenRatioQualifier-$shapeQualifier-land-$device-$pixelDensity-keyshidden-$nav\"")
          } else {
            println ("const val ${name}Land = \"w${widthDp}dp-h${heightDp}dp-$screenSize-$screenRatioQualifier-$shapeQualifier-land-$device-$pixelDensity-keyshidden-$nav\"")
            println ("const val ${name}Port = \"w${widthDp}dp-h${heightDp}dp-$screenSize-$screenRatioQualifier-$shapeQualifier-port-$device-$pixelDensity-keyshidden-$nav\"")
          }
        }
      }
  }

  private fun NodeList.toList(): List<Node> {
    val list = mutableListOf<Node>()
    for (i in 0 until length) {
      list.add(item(i))
    }
    return list
  }
}