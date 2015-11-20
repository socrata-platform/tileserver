package com.socrata.test.common

import scala.language.implicitConversions

import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.curator.CuratedServiceClient
import org.mockito.Mockito.mock

import com.socrata.http.client.{HttpClient, RequestBuilder, Response}
import com.socrata.http.server.{HttpRequest, HttpResponse}

import com.socrata.tileserver.mocks

import UnusedSugarCommon._

/** Extend this to create a customized `UnusedSugarCommon` trait for your project.
  *
  * Once an `UnusedSugar` trait has been mixed in `Unused` can be used in place of
  * any value that is irrelevant to the provided test (that is, it isn't
  * exercised on the current codepath).
  *s
  * NOTE: `UnusedSugar` will need to include both of the following:
  * ```
  * import com.socrata.test.common.UnusedSugarCommon
  * import com.socrata.test.common.UnusedSugarCommon._
  * ```
  *
  * This is designed to compliment using ScalaCheck, since adding generators for
  * arguments that aren't exercised is tedious.
  *
  * The pattern that exists here can be followed to add support for new types.
  * NOTE: If you are adding custom types you may need to inherit directly from
  * `UnusedSugarSimple` as some of these implicit conversions may conflict due
  * to type erasure.
  */
trait UnusedSugarCommon extends UnusedSugarSimple {
  val resourceScope = rs

  // Scala Collections
  implicit def unusedToArray[A: Manifest](u: UnusedValue): Array[A] = Array[A]()
  implicit def unusedToIndexedSeq[A](u: UnusedValue):IndexedSeq[A] = IndexedSeq.empty
  implicit def unusedToIterable[A](u: UnusedValue): Iterable[A] = Iterable.empty
  implicit def unusedToList[A](u: UnusedValue): List[A] = List.empty
  implicit def unusedToMap[A, B](u: UnusedValue): Map[A, B] = Map.empty
  implicit def unusedToOption[A](u: UnusedValue): Option[A] = None
  implicit def unusedToSeq[A](u: UnusedValue): Seq[A] = Seq.empty
  implicit def unusedToSet[A](u: UnusedValue): Set[A] = Set.empty

  // Java Collections
  implicit def unusedToJavaList[T](u: UnusedValue): java.util.List[T] =
    new java.util.ArrayList()
  implicit def unusedToJavaMap[K, V](u: UnusedValue): java.util.Map[K, V] =
    new java.util.HashMap()
  implicit def unusedToJavaQueue[T](u: UnusedValue): java.util.Queue[T] =
    new java.util.LinkedList()
  implicit def unusedToJavaSet[T](u: UnusedValue): java.util.Set[T] =
    new java.util.HashSet()
  implicit def unusedToJavaSortedMap[K, V](u: UnusedValue): java.util.SortedMap[K, V] =
    new java.util.TreeMap()
  implicit def unusedToJavaSortedSet[T](u: UnusedValue): java.util.SortedSet[T] =
    new java.util.TreeSet()

  // Optional Dependencies.
  implicit def unusedToCuratedServiceClient(u: UnusedValue): CuratedServiceClient =
    mock(classOf[CuratedServiceClient])

  implicit def unusedToResourceScope(u: UnusedValue): ResourceScope = resourceScope

  implicit def unusedToRespToHttpResponse(u: UnusedValue): Response => HttpResponse =
    r => mock(classOf[HttpResponse])

  implicit def unusedToHttpRequest(u: UnusedValue): HttpRequest =
    mocks.StaticRequest()

  implicit def unusedToRequestBuilder(u: UnusedValue): RequestBuilder =
    RequestBuilder(UnusedObj)

  implicit def unusedToHttpClient(u: UnusedValue): HttpClient =
    mocks.StaticHttpClient(UnusedObj)
}

/** This can be imported instead of extending the state to get similar functionality. */
object UnusedSugarCommon extends UnusedSugarCommon {
  type UnusedValue = UnusedSugarSimple.UnusedValue

  // This has to live here so the companion object can extend the trait.
  private object UnusedObj extends UnusedValue

  private val rs = new ResourceScope
}
