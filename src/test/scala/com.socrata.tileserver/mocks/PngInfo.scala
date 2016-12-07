package com.socrata.tileserver
package mocks

import UnusedSugar._
import gen.Extensions._
import util.RequestInfo

class PngInfo(val ext: String,
              override val style: Option[String],
              override val overscan: Option[Int])
    extends RequestInfo(Unused, Unused, Unused, Unused, ext, None)

object PngInfo {
  def apply(overscan: Int): PngInfo = new PngInfo(Unused, None, Some(overscan))

  def apply(ext: String): PngInfo = apply(ext, true)

  def apply(ext: String, complete: Boolean): PngInfo = if (ext == Png.name && complete) {
    new PngInfo(ext, Some(Unused), Some(Unused))
  } else {
    new PngInfo(ext, None, None)
  }

  def apply(ext: String,
            style: String,
            overscan: Int): PngInfo = if (ext == Png.name) {
    new PngInfo(ext, Some(style), Some(overscan))
  } else {
    new PngInfo(ext, None, None)
  }

  def apply(ext: String,
            style: Option[String],
            overscan: Option[Int]): PngInfo = new PngInfo(ext, style, overscan)
}
