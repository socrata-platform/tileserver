package com.socrata.tileserver.handlers

/** The file type that this object handles. */
trait FileType {
  def extension: String
}
