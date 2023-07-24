package uk.gov.nationalarchives

import cats.effect.unsafe.implicits.global

import java.io.ByteArrayInputStream
object LambdaRunner extends App {



  val in = new ByteArrayInputStream("".getBytes())
  val d = new Lambda().handleRequest(in, null, null)
  println(d)
}
