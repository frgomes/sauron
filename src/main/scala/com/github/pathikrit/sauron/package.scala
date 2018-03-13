package com.github.pathikrit

import scala.reflect.macros.blackbox

package object sauron {

  type Setter[A] = A => A                 // a function that updates a field
  type Updater[A, B] = Setter[B] => A     // given a setter to update a nested field, return back the updated parent
  type Lens[A, B] = A => Updater[A, B]    // lens from A to B means given an A, return an updater on A,B (see above)
  type ~~>[A, B] = Lens[A, B]             // for those who prefer symbols

  def lens[A, B](obj: A)(path: A => B): Updater[A, B] = macro lensImpl[A, B]

  def lensImpl[A, B](c: blackbox.Context)(obj: c.Expr[A])(path: c.Expr[A => B]): c.Tree = {
    import c.universe._

    def split(accessor: c.Tree): List[c.TermName] = accessor match {      // (_.p.q.r) -> List(p, q, r)
      case q"$pq.$r" => split(pq) :+ r
      case _: Ident => Nil
      case _ => c.abort(c.enclosingPosition, s"[sauron] Unsupported path element: $accessor")
    }

    def nest(prefix: c.Tree, f: TermName, suffix: List[TermName]): c.Tree = suffix match {
      case p :: ps => q"$prefix.copy($p = ${nest(q"$prefix.$p", f, ps)})" // Recursively nest the f
      case Nil => q"$f($prefix)"                                          // Reached the end, apply f
    }

    path.tree match {
      case q"($_) => $accessor" =>
        val f = TermName(c.freshName())
        val fParamTree = q"val $f = ${q""}"
        q"{$fParamTree => ${nest(obj.tree, f, split(accessor))}}"
      case _ => c.abort(c.enclosingPosition, s"[sauron] Path must have shape: _.a.b.c.(...); got: ${path.tree}")
    }
  }

  implicit class LensOps[A, B](val f: A ~~> B) extends AnyVal {
    def andThenLens[C](g: B ~~> C): A ~~> C = x => y => f(x)(g(_)(y))
    def composeLens[C](g: C ~~> A): C ~~> B = g andThenLens f
  }

  implicit class UpdaterOps[A, B](val f: Updater[A, B]) extends AnyVal {
    def setTo(v: B): A = f(_ => v)
  }
}
