package com.socrata.tileserver.exceptions

case class FailedRenderException(message: String) extends Exception(message)
