package com.socrata.tileserver
package services

import com.socrata.http.server.responses._

class VersionServiceTest extends TestBase with UnusedSugar {
  test("Endpoint must return health = alive") {
    val resp = unpackResponse(VersionService.get(Unused))

    resp.status must equal (OK.statusCode)
    resp.contentType must equal ("application/json; charset=UTF-8")
    resp.body.toLowStr must include ("health")
    resp.body.toLowStr must include ("alive")
  }
}
