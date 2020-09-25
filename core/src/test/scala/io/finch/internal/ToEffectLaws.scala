package io.finch.internal

import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import cats.{Applicative, Eq}
import io.finch.MissingInstances
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

abstract trait ToEffectLaws[F[_], G[_], A] extends Laws with MissingInstances with AllInstances {

  def F: Applicative[F]
  def G: Applicative[G]
  def extract: G[A] => A

  def T: ToAsync[F, G]

  def nTransformation(f: => F[A], g: G[A], fn: A => A): IsEq[A] =
    extract(G.map(T(f))(fn)) <-> extract(T(F.map(f)(fn)))

  def all(implicit A: Arbitrary[A], E: Eq[A]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "natural transformation" -> Prop.forAll { a: A => nTransformation(F.pure(a), G.pure(a), identity) }
  )

}

object ToEffectLaws {

  def apply[F[_]: Applicative, G[_]: Applicative, A](e: G[A] => A)(implicit
      t: ToAsync[F, G]
  ): ToEffectLaws[F, G, A] =
    new ToEffectLaws[F, G, A] {
      val F: Applicative[F] = implicitly[Applicative[F]]
      val G: Applicative[G] = implicitly[Applicative[G]]
      val T: ToAsync[F, G] = t
      val extract: G[A] => A = e
    }

}
