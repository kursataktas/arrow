package arrow.core.extensions

import arrow.Kind
import arrow.core.EQ
import arrow.core.Either
import arrow.core.Eval
import arrow.core.ForOption
import arrow.core.GT
import arrow.core.Ior
import arrow.core.LT
import arrow.core.None
import arrow.core.Option
import arrow.core.OptionOf
import arrow.core.Ordering
import arrow.core.SequenceK
import arrow.core.Some
import arrow.core.Tuple2
import arrow.core.extensions.option.apply.apply
import arrow.core.extensions.option.monad.map
import arrow.core.extensions.option.monad.monad
import arrow.core.fix
import arrow.core.identity
import arrow.core.k
import arrow.core.orElse
import arrow.core.some
import arrow.core.toT
import arrow.typeclasses.Align
import arrow.typeclasses.Alternative
import arrow.typeclasses.Applicative
import arrow.typeclasses.ApplicativeError
import arrow.typeclasses.Apply
import arrow.typeclasses.Crosswalk
import arrow.typeclasses.Eq
import arrow.typeclasses.EqK
import arrow.typeclasses.Foldable
import arrow.typeclasses.Functor
import arrow.typeclasses.FunctorFilter
import arrow.typeclasses.Hash
import arrow.typeclasses.Monad
import arrow.typeclasses.MonadCombine
import arrow.typeclasses.MonadError
import arrow.typeclasses.MonadFilter
import arrow.typeclasses.MonadFx
import arrow.typeclasses.MonadPlus
import arrow.typeclasses.MonadSyntax
import arrow.typeclasses.Monoid
import arrow.typeclasses.MonoidK
import arrow.typeclasses.Monoidal
import arrow.typeclasses.Order
import arrow.typeclasses.OrderDeprecation
import arrow.typeclasses.Repeat
import arrow.typeclasses.Selective
import arrow.typeclasses.Semialign
import arrow.typeclasses.Semigroup
import arrow.typeclasses.SemigroupK
import arrow.typeclasses.Semigroupal
import arrow.typeclasses.Show
import arrow.typeclasses.Traverse
import arrow.typeclasses.TraverseFilter
import arrow.typeclasses.Unalign
import arrow.typeclasses.Unzip
import arrow.typeclasses.Zip
import arrow.typeclasses.hashWithSalt
import arrow.core.extensions.traverse as optionTraverse
import arrow.core.extensions.traverseFilter as optionTraverseFilter
import arrow.core.select as optionSelect

@Deprecated(
  "Typeclass instance have been moved to the companion object of the typeclass.",
  ReplaceWith("Semigroup.option()", "arrow.core.option", "arrow.typeclasses.Semigroup"),
  DeprecationLevel.WARNING
)
interface OptionSemigroup<A> : Semigroup<Option<A>> {

  fun SG(): Semigroup<A>

  override fun Option<A>.combine(b: Option<A>): Option<A> =
    when (this) {
      is Some<A> -> when (b) {
        is Some<A> -> Some(SG().run { t.combine(b.t) })
        None -> this
      }
      None -> b
    }
}

@Deprecated(
  message = "Semigroupal typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionSemigroupal : Semigroupal<ForOption> {
  override fun <A, B> Kind<ForOption, A>.product(fb: Kind<ForOption, B>): Kind<ForOption, Tuple2<A, B>> =
    fb.fix().ap(this.map { a: A -> { b: B -> Tuple2(a, b) } })
}

@Deprecated(
  message = "Monoidal typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionMonoidal : Monoidal<ForOption>, OptionSemigroupal {
  override fun <A> identity(): Kind<ForOption, A> = None
}

@Deprecated(
  "Typeclass instance have been moved to the companion object of the typeclass.",
  ReplaceWith("Monoid.option()", "arrow.core.option", "arrow.typeclasses.Monoid"),
  DeprecationLevel.WARNING
)
interface OptionMonoid<A> : Monoid<Option<A>>, OptionSemigroup<A> {
  override fun SG(): Semigroup<A>
  override fun empty(): Option<A> = None
}

@Deprecated(
  message = "ApplicativeError typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionApplicativeError : ApplicativeError<ForOption, Unit>, OptionApplicative {
  override fun <A> raiseError(e: Unit): Option<A> =
    None

  override fun <A> OptionOf<A>.handleErrorWith(f: (Unit) -> OptionOf<A>): Option<A> =
    fix().orElse { f(Unit).fix() }
}

@Deprecated(
  message = "MonadError typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionMonadError : MonadError<ForOption, Unit>, OptionMonad {
  override fun <A> raiseError(e: Unit): OptionOf<A> =
    None

  override fun <A> OptionOf<A>.handleErrorWith(f: (Unit) -> OptionOf<A>): Option<A> =
    fix().orElse { f(Unit).fix() }
}

@Deprecated(
  message = "Eq typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionEq<A> : Eq<Option<A>> {

  fun EQ(): Eq<A>

  override fun Option<A>.eqv(b: Option<A>): Boolean = when (this) {
    is Some -> when (b) {
      None -> false
      is Some -> EQ().run { t.eqv(b.t) }
    }
    None -> when (b) {
      None -> true
      is Some -> false
    }
  }
}

@Deprecated(
  message = "Show typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionShow<A> : Show<Option<A>> {
  fun SA(): Show<A>
  override fun Option<A>.show(): String = show(SA())
}

@Deprecated(
  message = "Functor typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionFunctor : Functor<ForOption> {
  override fun <A, B> OptionOf<A>.map(f: (A) -> B): Option<B> =
    fix().map(f)
}

@Deprecated(
  message = "Apply typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionApply : Apply<ForOption> {
  override fun <A, B> OptionOf<A>.ap(ff: OptionOf<(A) -> B>): Option<B> =
    fix().ap(ff)

  override fun <A, B> OptionOf<A>.map(f: (A) -> B): Option<B> =
    fix().map(f)

  override fun <A, B> Kind<ForOption, A>.apEval(ff: Eval<Kind<ForOption, (A) -> B>>): Eval<Kind<ForOption, B>> =
    fix().fold({ Eval.now(None) }, { v -> ff.map { it.fix().map { f -> f(v) } } })
}

@Deprecated(
  message = "Applicative typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionApplicative : Applicative<ForOption>, OptionApply {
  override fun <A, B> OptionOf<A>.ap(ff: OptionOf<(A) -> B>): Option<B> =
    fix().ap(ff)

  override fun <A, B> OptionOf<A>.map(f: (A) -> B): Option<B> =
    fix().map(f)

  override fun <A> just(a: A): Option<A> =
    Option.just(a)
}

@Deprecated(
  message = "Selective typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionSelective : Selective<ForOption>, OptionApplicative {
  override fun <A, B> OptionOf<Either<A, B>>.select(f: OptionOf<(A) -> B>): Option<B> =
    fix().optionSelect(f)
}

@Deprecated(
  message = "Monad typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionMonad : Monad<ForOption>, OptionApplicative {
  override fun <A, B> OptionOf<A>.ap(ff: OptionOf<(A) -> B>): Option<B> =
    fix().ap(ff)

  override fun <A, B> OptionOf<A>.flatMap(f: (A) -> OptionOf<B>): Option<B> =
    fix().flatMap(f)

  override fun <A, B> tailRecM(a: A, f: (A) -> OptionOf<Either<A, B>>): Option<B> =
    Option.tailRecM(a, f)

  override fun <A, B> OptionOf<A>.map(f: (A) -> B): Option<B> =
    fix().map(f)

  override fun <A> just(a: A): Option<A> =
    Option.just(a)

  override fun <A, B> OptionOf<Either<A, B>>.select(f: OptionOf<(A) -> B>): OptionOf<B> =
    fix().optionSelect(f)

  override val fx: MonadFx<ForOption>
    get() = OptionFxMonad
}

internal object OptionFxMonad : MonadFx<ForOption> {
  override val M: Monad<ForOption> = Option.monad()
  override fun <A> monad(c: suspend MonadSyntax<ForOption>.() -> A): Option<A> =
    super.monad(c).fix()
}

@Deprecated(
  message = "Foldable typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionFoldable : Foldable<ForOption> {
  override fun <A> OptionOf<A>.exists(p: (A) -> Boolean): Boolean =
    fix().exists(p)

  override fun <A, B> OptionOf<A>.foldLeft(b: B, f: (B, A) -> B): B =
    fix().foldLeft(b, f)

  override fun <A, B> OptionOf<A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> =
    fix().foldRight(lb, f)

  override fun <A> OptionOf<A>.forAll(p: (A) -> Boolean): Boolean =
    fix().forall(p)

  override fun <A> OptionOf<A>.isEmpty(): Boolean =
    fix().isEmpty()

  override fun <A> OptionOf<A>.nonEmpty(): Boolean =
    fix().nonEmpty()
}

@Deprecated(
  message = "SemigroupK typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionSemigroupK : SemigroupK<ForOption> {
  override fun <A> OptionOf<A>.combineK(y: OptionOf<A>): Option<A> =
    orElse { y.fix() }
}

@Deprecated(
  message = "MonoidK typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionMonoidK : MonoidK<ForOption> {
  override fun <A> empty(): Option<A> =
    Option.empty()

  override fun <A> OptionOf<A>.combineK(y: OptionOf<A>): Option<A> =
    orElse { y.fix() }
}

@Deprecated(
  "Applicative typeclass is deprecated, Replace with traverse, traverseEither or traverseValidated from arrow.core.*",
  level = DeprecationLevel.WARNING
)
fun <A, G, B> OptionOf<A>.traverse(GA: Applicative<G>, f: (A) -> Kind<G, B>): Kind<G, Option<B>> = GA.run {
  fix().fold({ just(None) }, { f(it).map { Some(it) } })
}

@Deprecated(
  "Applicative typeclass is deprecated, Replace with sequence, sequenceEither or sequenceValidated from arrow.core.*",
  level = DeprecationLevel.WARNING
)
fun <A, G> OptionOf<Kind<G, A>>.sequence(GA: Applicative<G>): Kind<G, Option<A>> =
  optionTraverse(GA, ::identity)

@Deprecated(
  "Applicative typeclass is deprecated, Replace with traverseFilter, traverseFilterEither or traverseFilterValidated from arrow.core.*",
  level = DeprecationLevel.WARNING
)
fun <A, G, B> OptionOf<A>.traverseFilter(GA: Applicative<G>, f: (A) -> Kind<G, Option<B>>): Kind<G, Option<B>> = GA.run {
  fix().fold({ just(None) }, f)
}

@Deprecated(
  message = "Traverse typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionTraverse : Traverse<ForOption> {
  override fun <A, B> OptionOf<A>.map(f: (A) -> B): Option<B> =
    fix().map(f)

  override fun <G, A, B> OptionOf<A>.traverse(AP: Applicative<G>, f: (A) -> Kind<G, B>): Kind<G, Option<B>> =
    optionTraverse(AP, f)

  override fun <A> OptionOf<A>.exists(p: (A) -> Boolean): Boolean =
    fix().exists(p)

  override fun <A, B> OptionOf<A>.foldLeft(b: B, f: (B, A) -> B): B =
    fix().foldLeft(b, f)

  override fun <A, B> OptionOf<A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> =
    fix().foldRight(lb, f)

  override fun <A> OptionOf<A>.forAll(p: (A) -> Boolean): Boolean =
    fix().forall(p)

  override fun <A> OptionOf<A>.isEmpty(): Boolean =
    fix().isEmpty()

  override fun <A> OptionOf<A>.nonEmpty(): Boolean =
    fix().nonEmpty()
}

@Deprecated(
  message = "Hash typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionHash<A> : Hash<Option<A>> {

  fun HA(): Hash<A>

  override fun Option<A>.hashWithSalt(salt: Int): Int =
    fold({ salt.hashWithSalt(0) }, { v -> HA().run { v.hashWithSalt(salt.hashWithSalt(1)) } })
}

@Deprecated(OrderDeprecation)
interface OptionOrder<A> : Order<Option<A>> {
  fun OA(): Order<A>
  override fun Option<A>.compare(b: Option<A>): Ordering = fold(
    {
      b.fold({ EQ }, { LT })
    },
    { a1 ->
      b.fold({ GT }, { a2 -> OA().run { a1.compare(a2) } })
    }
  )
}

@Deprecated(
  message = "FunctorFilter typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionFunctorFilter : FunctorFilter<ForOption> {
  override fun <A, B> Kind<ForOption, A>.filterMap(f: (A) -> Option<B>): Option<B> =
    fix().filterMap(f)

  override fun <A, B> Kind<ForOption, A>.map(f: (A) -> B): Option<B> =
    fix().map(f)
}

fun <A> Option.Companion.fx(c: suspend MonadSyntax<ForOption>.() -> A): Option<A> =
  Option.monad().fx.monad(c).fix()

@Deprecated(
  message = "MonadCombine typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionMonadCombine : MonadCombine<ForOption>, OptionAlternative {
  override fun <A> empty(): Option<A> =
    Option.empty()

  override fun <A, B> Kind<ForOption, A>.filterMap(f: (A) -> Option<B>): Option<B> =
    fix().filterMap(f)

  override fun <A, B> Kind<ForOption, A>.ap(ff: Kind<ForOption, (A) -> B>): Option<B> =
    fix().ap(ff)

  override fun <A, B> Kind<ForOption, A>.flatMap(f: (A) -> Kind<ForOption, B>): Option<B> =
    fix().flatMap(f)

  override fun <A, B> tailRecM(a: A, f: kotlin.Function1<A, OptionOf<Either<A, B>>>): Option<B> =
    Option.tailRecM(a, f)

  override fun <A, B> Kind<ForOption, A>.map(f: (A) -> B): Option<B> =
    fix().map(f)

  override fun <A, B, Z> Kind<ForOption, A>.map2(fb: Kind<ForOption, B>, f: (Tuple2<A, B>) -> Z): Option<Z> =
    fix().map2(fb, f)

  override fun <A> just(a: A): Option<A> =
    Option.just(a)

  override fun <A> Kind<ForOption, A>.some(): Option<SequenceK<A>> =
    fix().fold(
      { Option.empty() },
      {
        Sequence {
          object : Iterator<A> {
            override fun hasNext(): Boolean = true

            override fun next(): A = it
          }
        }.k().just().fix()
      }
    )

  override fun <A> Kind<ForOption, A>.many(): Option<SequenceK<A>> =
    fix().fold(
      { emptySequence<A>().k().just().fix() },
      {
        Sequence {
          object : Iterator<A> {
            override fun hasNext(): Boolean = true

            override fun next(): A = it
          }
        }.k().just().fix()
      }
    )
}

@Deprecated(
  message = "TraverseFilter typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionTraverseFilter : TraverseFilter<ForOption> {
  override fun <A> Kind<ForOption, A>.filter(f: (A) -> Boolean): Option<A> =
    fix().filter(f)

  override fun <G, A, B> Kind<ForOption, A>.traverseFilter(AP: Applicative<G>, f: (A) -> Kind<G, Option<B>>): Kind<G, Option<B>> =
    optionTraverseFilter(AP, f)

  override fun <A, B> Kind<ForOption, A>.map(f: (A) -> B): Option<B> =
    fix().map(f)

  override fun <G, A, B> Kind<ForOption, A>.traverse(AP: Applicative<G>, f: (A) -> Kind<G, B>): Kind<G, Option<B>> =
    optionTraverse(AP, f)

  override fun <A> Kind<ForOption, A>.exists(p: (A) -> Boolean): Boolean =
    fix().exists(p)

  override fun <A, B> Kind<ForOption, A>.foldLeft(b: B, f: (B, A) -> B): B =
    fix().foldLeft(b, f)

  override fun <A, B> Kind<ForOption, A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> =
    fix().foldRight(lb, f)

  override fun <A> OptionOf<A>.forAll(p: (A) -> Boolean): Boolean =
    fix().forall(p)

  override fun <A> Kind<ForOption, A>.isEmpty(): Boolean =
    fix().isEmpty()

  override fun <A> Kind<ForOption, A>.nonEmpty(): Boolean =
    fix().nonEmpty()
}

@Deprecated(
  message = "MonadFilter typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionMonadFilter : MonadFilter<ForOption> {
  override fun <A> empty(): Option<A> =
    Option.empty()

  override fun <A, B> Kind<ForOption, A>.filterMap(f: (A) -> Option<B>): Option<B> =
    fix().filterMap(f)

  override fun <A, B> Kind<ForOption, A>.ap(ff: Kind<ForOption, (A) -> B>): Option<B> =
    fix().ap(ff)

  override fun <A, B> Kind<ForOption, A>.flatMap(f: (A) -> Kind<ForOption, B>): Option<B> =
    fix().flatMap(f)

  override fun <A, B> tailRecM(a: A, f: kotlin.Function1<A, OptionOf<Either<A, B>>>): Option<B> =
    Option.tailRecM(a, f)

  override fun <A, B> Kind<ForOption, A>.map(f: (A) -> B): Option<B> =
    fix().map(f)

  override fun <A, B, Z> Kind<ForOption, A>.map2(fb: Kind<ForOption, B>, f: (Tuple2<A, B>) -> Z): Option<Z> =
    fix().map2(fb, f)

  override fun <A> just(a: A): Option<A> =
    Option.just(a)
}

@Deprecated(
  message = "Alternative typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionAlternative : Alternative<ForOption>, OptionApplicative {
  override fun <A> empty(): Kind<ForOption, A> = None
  override fun <A> Kind<ForOption, A>.orElse(b: Kind<ForOption, A>): Kind<ForOption, A> =
    if (fix().isEmpty()) b
    else this

  override fun <A> Kind<ForOption, A>.lazyOrElse(b: () -> Kind<ForOption, A>): Kind<ForOption, A> =
    if (fix().isEmpty()) b()
    else this
}

@Deprecated(
  message = "EqK typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionEqK : EqK<ForOption> {
  override fun <A> Kind<ForOption, A>.eqK(other: Kind<ForOption, A>, EQ: Eq<A>) =
    (this.fix() to other.fix()).let { (a, b) ->
      when (a) {
        is None -> {
          when (b) {
            is None -> true
            is Some -> false
          }
        }
        is Some -> {
          when (b) {
            is None -> false
            is Some -> EQ.run { a.t.eqv(b.t) }
          }
        }
      }
    }
}

@Deprecated(
  message = "Semialign typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionSemialign : Semialign<ForOption>, OptionFunctor {
  override fun <A, B> align(a: Kind<ForOption, A>, b: Kind<ForOption, B>): Kind<ForOption, Ior<A, B>> =
    Ior.fromOptions(a.fix(), b.fix())
}

@Deprecated(
  message = "Align typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionAlign : Align<ForOption>, OptionSemialign {
  override fun <A> empty(): Kind<ForOption, A> = Option.empty()
}

@Deprecated(
  message = "Unalign typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionUnalign : Unalign<ForOption>, OptionSemialign {
  override fun <A, B> unalign(ior: Kind<ForOption, Ior<A, B>>): Tuple2<Kind<ForOption, A>, Kind<ForOption, B>> =
    when (val a = ior.fix()) {
      is None -> None toT None
      is Some -> when (val b = a.t) {
        is Ior.Left -> b.value.some() toT None
        is Ior.Right -> None toT b.value.some()
        is Ior.Both -> b.leftValue.some() toT b.rightValue.some()
      }
    }
}

@Deprecated(
  message = "Zip typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionZip : Zip<ForOption>, OptionSemialign {
  override fun <A, B> Kind<ForOption, A>.zip(other: Kind<ForOption, B>): Kind<ForOption, Tuple2<A, B>> =
    Option.apply().tupledN(this, other)
}

@Deprecated(
  message = "Repeat typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionRepeat : Repeat<ForOption>, OptionZip {
  override fun <A> repeat(a: A): Kind<ForOption, A> =
    Option.just(a)
}

@Deprecated(
  message = "Unzip typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionUnzip : Unzip<ForOption>, OptionZip {
  override fun <A, B> Kind<ForOption, Tuple2<A, B>>.unzip(): Tuple2<Kind<ForOption, A>, Kind<ForOption, B>> =
    fix().fold(
      { Option.empty<A>() toT Option.empty() },
      { it.a.some() toT it.b.some() }
    )
}

@Deprecated(
  message = "Crosswalk typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionCrosswalk : Crosswalk<ForOption>, OptionFunctor, OptionFoldable {
  override fun <F, A, B> crosswalk(ALIGN: Align<F>, a: Kind<ForOption, A>, fa: (A) -> Kind<F, B>): Kind<F, Kind<ForOption, B>> =
    when (val e = a.fix()) {
      is None -> ALIGN.run { empty<B>().map { Option.empty<B>() } }
      is Some -> ALIGN.run { fa(e.t).map { Option.just(it) } }
    }
}

@Deprecated(
  message = "MonadPlus typeclass is deprecated and will be removed in 0.13.0. Use concrete methods on Option",
  level = DeprecationLevel.WARNING
)
interface OptionMonadPlus : MonadPlus<ForOption>, OptionMonad, OptionAlternative
