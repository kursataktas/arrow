package arrow.fx.resilience

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.identity
import arrow.core.left
import arrow.core.merge
import arrow.core.nonFatalOrThrow
import arrow.core.right
import arrow.core.some
import arrow.fx.resilience.Schedule.Companion.identity
import arrow.fx.resilience.Schedule.Decision.Continue
import arrow.fx.resilience.Schedule.Decision.Done
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.jvm.JvmInline
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.currentCoroutineContext

public typealias Next<Input, Output> =
  suspend (Input) -> Schedule.Decision<Input, Output>

@JvmInline
public value class Schedule<Input, Output>(
  public val step: Next<Input, Output>
) {

  /** Repeat the schedule, and uses [block] as [Input] for the [step] function. */
  public suspend fun repeat(block: suspend () -> Input): Output =
    repeatOrElse(block) { e, _ -> throw e }

  /**
   * Repeat the schedule, and uses [block] as [Input] for the [step] function.
   * If the [step] function throws an exception, it will be caught and passed to [orElse].
   */
  public suspend fun repeatOrElse(
    block: suspend () -> Input,
    orElse: suspend (error: Throwable, output: Output?) -> Output
  ): Output =
    repeatOrElseEither(block, orElse).fold(::identity, ::identity)

  /**
   * Repeat the schedule, and uses [block] as [Input] for the [step] function.
   * If the [step] function throws an exception, it will be caught and passed to [orElse].
   * The resulting [Either] indicates if the [step] function threw an exception or not.
   */
  public suspend fun <A> repeatOrElseEither(
    block: suspend () -> Input,
    orElse: suspend (error: Throwable, output: Output?) -> A
  ): Either<A, Output> {
    var step: Next<Input, Output> = step
    var state: Option<Output> = None

    while (true) {
      currentCoroutineContext().ensureActive()
      try {
        val a = block.invoke()
        when (val decision = step(a)) {
          is Continue -> {
            if (decision.delay != ZERO) delay(decision.delay)
            state = decision.output.some()
            step = decision.next
          }

          is Done -> return Either.Right(decision.output)
        }
      } catch (e: Throwable) {
        return Either.Left(orElse(e.nonFatalOrThrow(), state.getOrNull()))
      }
    }
  }

  /** Transforms every [Output]'ed value of `this` schedule using [transform]. */
  public fun <A> map(transform: suspend (output: Output) -> A): Schedule<Input, A> {
    suspend fun loop(input: Input, self: Next<Input, Output>): Decision<Input, A> =
      when (val decision = self(input)) {
        is Continue -> Continue(transform(decision.output), decision.delay) { loop(it, decision.next) }
        is Done -> Done(transform(decision.output))
      }

    return Schedule { input -> loop(input, step) }
  }

  /**
   * Runs `this` schedule until [Done], and then runs [other] until [Done].
   * Wrapping the output of `this` in [Either.Left], and the output of [other] in [Either.Right].
   */
  public infix fun <A> andThen(other: Schedule<Input, A>): Schedule<Input, Either<Output, A>> =
    andThen(other, { it.left() }) { it.right() }

  /**
   * Runs `this` schedule, and transforms the output of this schedule using [ifLeft],
   * When `this` schedule is [Done], it runs [other] schedule, and transforms the output using [ifRight].
   */
  public fun <A, B> andThen(
    other: Schedule<Input, A>,
    ifLeft: suspend (Output) -> B,
    ifRight: suspend (A) -> B
  ): Schedule<Input, B> {
    suspend fun loop(input: Input, self: Next<Input, A>): Decision<Input, B> =
      when (val decision = self(input)) {
        is Done -> Done(ifRight(decision.output))
        is Continue -> Continue(ifRight(decision.output), decision.delay) {
          loop(input, decision.next)
        }
      }

    suspend fun loop(input: Input, self: Next<Input, Output>): Decision<Input, B> =
      when (val decision = self(input)) {
        is Continue -> Continue(ifLeft(decision.output), decision.delay) { loop(it, decision.next) }
        is Done -> Continue(ifLeft(decision.output), ZERO) { loop(input, other.step) }
      }

    return Schedule { input -> loop(input, step) }
  }

  /**
   * Pipes the output of `this` [Schedule] to the input of the [other] [Schedule].
   * Similar to `|>` in F# but for [Schedule].
   */
  public infix fun <A> pipe(other: Schedule<Output, A>): Schedule<Input, A> {
    suspend fun loop(input: Input, self: Next<Input, Output>, other: Next<Output, A>): Decision<Input, A> =
      when (val decision = self(input)) {
        is Done -> Done(other(decision.output).output)
        is Continue -> when (val decision2 = other(decision.output)) {
          is Done -> Done(decision2.output)
          is Continue -> Continue(decision2.output, decision.delay + decision2.delay) {
            loop(it, decision.next, decision2.next)
          }
        }
      }

    return Schedule { input -> loop(input, step, other.step) }
  }

  /** Runs `this` [Schedule] _while_ the [predicate] of [Input] and [Output] returns `false`. */
  public fun doWhile(predicate: suspend (Input, Output) -> Boolean): Schedule<Input, Output> {
    suspend fun loop(input: Input, self: Next<Input, Output>): Decision<Input, Output> =
      when (val decision = self(input)) {
        is Continue ->
          if (predicate(input, decision.output)) Continue(decision.output, decision.delay) { loop(it, decision.next) }
          else Done(decision.output)

        is Done -> decision
      }

    return Schedule { input -> loop(input, step) }
  }

  /**
   * Runs the [Schedule] _until_ the [predicate] of [Input] and [Output] returns true.
   * Inverse version of [doWhile].
   */
  public fun doUntil(predicate: suspend (input: Input, output: Output) -> Boolean): Schedule<Input, Output> =
    doWhile { input, output -> !predicate(input, output) }

  /**
   * Adds a logging action to the [Schedule].
   */
  public fun log(action: suspend (input: Input, output: Output) -> Unit): Schedule<Input, Output> =
    doWhile { input, output ->
      action(input, output)
      true
    }

  /**
   * Modify [Continue.delay] by the given function [transform].
   */
  public fun delayed(transform: suspend (Output, Duration) -> Duration): Schedule<Input, Output> {
    suspend fun loop(input: Input, self: Next<Input, Output>): Decision<Input, Output> =
      when (val decision = self(input)) {
        is Continue -> Continue(decision.output, transform(decision.output, decision.delay)) { loop(it, decision.next) }
        is Done -> decision
      }

    return Schedule { input -> loop(input, step) }
  }

  /** Adds a [Random] jitter to the delay of the [Schedule]. */
  public fun jittered(
    min: Double = 0.0,
    max: Double = 1.0,
    random: Random = Random.Default
  ): Schedule<Input, Output> =
    delayed { _, duration -> duration * random.nextDouble(min, max) }

  public fun mapDecision(f: suspend (Decision<Input, Output>) -> Decision<Input, Output>): Schedule<Input, Output> {
    suspend fun loop(input: Input, self: Next<Input, Output>): Decision<Input, Output> =
      f(self(input))

    return Schedule { input -> loop(input, step) }
  }

  /**
   * Collects all the [Output] of the [Schedule] into a [List].
   * This is useful in combination with [identity] to collect all the inputs.
   */
  public fun collect(): Schedule<Input, List<Output>> =
    fold(emptyList()) { acc, out -> acc + out }

  /**
   * Folds all the [Output] of the [Schedule] into a [List].
   * This is useful in combination with [identity] to fold all the [Input] into a final value [B].
   * If one of the [Schedule]s is done, the other [Schedule] is not executed anymore.
   */
  public fun <B> fold(b: B, f: suspend (B, Output) -> B): Schedule<Input, B> {
    suspend fun loop(input: Input, b: B, self: Next<Input, Output>): Decision<Input, B> =
      when (val decision = self(input)) {
        is Continue -> f(b, decision.output).let { b2 ->
          Continue(b2, decision.delay) { loop(it, b2, decision.next) }
        }

        is Done -> Done(b)
      }

    return Schedule { loop(it, b, step) }
  }

  /**
   * Combines two [Schedule]s into one, ignoring the output of [other] [Schedule].
   * It chooses the longest delay between the two [Schedule]s.
   * If one of the [Schedule]s is done, the other [Schedule] is not executed anymore.
   */
  public infix fun <B> zipLeft(other: Schedule<Input, B>): Schedule<Input, Output> =
    and(other) { input, _ -> input }

  /**
   * Combines two [Schedule]s into one, ignoring the output of `this` [Schedule].
   * It chooses the longest delay between the two [Schedule]s.
   * If one of the [Schedule]s is done, the other [Schedule] is not executed anymore.
   */
  public infix fun <B> zipRight(other: Schedule<Input, B>): Schedule<Input, B> =
    and(other) { _, b -> b }

  /**
   * Combines two [Schedule]s into one by combining the output of both [Schedule]s into a [Pair].
   * It chooses the longest delay between the two [Schedule]s.
   * If one of the [Schedule]s is done, the other [Schedule] is not executed anymore.
   */
  public infix fun <B> and(other: Schedule<Input, B>): Schedule<Input, Pair<Output, B>> =
    and(other, ::Pair)

  /**
   * Combines two [Schedule]s into one by transforming the output of both [Schedule]s using [transform].
   * It chooses the longest delay between the two [Schedule]s.
   * If one of the [Schedule]s is done, the other [Schedule] is not executed anymore.
   */
  public fun <B, C> and(
    other: Schedule<Input, B>,
    transform: suspend (output: Output, b: B) -> C
  ): Schedule<Input, C> = and(other, transform) { a, b -> maxOf(a, b) }

  /**
   * Combines two [Schedule]s into one by transforming the output of both [Schedule]s using [transform].
   * It combines the delay of both [Schedule]s using [combineDuration].
   * If one of the [Schedule]s is done, the other [Schedule] is not executed anymore.
   */
  public fun <B, C> and(
    other: Schedule<Input, B>,
    transform: suspend (output: Output, b: B) -> C,
    combineDuration: suspend (left: Duration, right: Duration) -> Duration
  ): Schedule<Input, C> {
    suspend fun loop(
      input: Input,
      self: Next<Input, Output>,
      that: Next<Input, B>
    ): Decision<Input, C> {
      val left = self(input)
      val right = that(input)
      return if (left is Continue && right is Continue) Continue(
        transform(left.output, right.output),
        combineDuration(left.delay, right.delay)
      ) {
        loop(it, left.next, right.next)
      } else Done(transform(left.output, right.output))
    }

    return Schedule { input ->
      loop(input, step, other.step)
    }
  }

  /**
   * Combines two [Schedule]s into one by transforming the output of both [Schedule]s using [transform].
   * It combines the delay of both [Schedule]s using [combineDuration].
   * It continues to execute both [Schedule]s until both are done,
   * padding the output and duration with `null` if one of the [Schedule]s is done.
   */
  public fun <B, C> or(
    other: Schedule<Input, B>,
    transform: suspend (output: Output?, b: B?) -> C,
    combineDuration: suspend (left: Duration?, right: Duration?) -> Duration
  ): Schedule<Input, C> {
    suspend fun loop(
      input: Input,
      self: Next<Input, Output>?,
      that: Next<Input, B>?
    ): Decision<Input, C> =
      when (val left = self?.invoke(input)) {
        is Continue -> when (val right = that?.invoke(input)) {
          is Continue -> Continue(
            transform(left.output, right.output),
            combineDuration(left.delay, right.delay)
          ) {
            loop(it, left.next, right.next)
          }

          is Done -> Continue(
            transform(left.output, right.output),
            combineDuration(left.delay, null)
          ) {
            loop(it, left.next, null)
          }

          null -> Continue(
            transform(left.output, null),
            combineDuration(left.delay, null)
          ) {
            loop(it, left.next, null)
          }
        }

        is Done -> when (val right = that?.invoke(input)) {
          is Continue -> Continue(
            transform(left.output, right.output),
            combineDuration(null, right.delay)
          ) {
            loop(it, null, right.next)
          }

          is Done -> Done(transform(left.output, right.output))
          null -> Done(transform(left.output, null))
        }

        null -> when (val right = that?.invoke(input)) {
          is Continue -> Continue(
            transform(null, right.output),
            combineDuration(null, right.delay)
          ) {
            loop(it, null, right.next)
          }

          is Done -> Done(transform(null, right.output))
          null -> Done(transform(null, null))
        }
      }

    return Schedule { input ->
      loop(input, step, other.step)
    }
  }

  public companion object {

    /** Create a [Schedule] that continues `while` [predicate] returns true. */
    public fun <Input> doWhile(predicate: suspend (input: Input, output: Input) -> Boolean): Schedule<Input, Input> =
      identity<Input>().doWhile(predicate)

    /** Creates a [Schedule] that continues `until` [predicate] returns true. */
    public fun <Input> doUntil(predicate: suspend (input: Input, output: Input) -> Boolean): Schedule<Input, Input> =
      identity<Input>().doUntil(predicate)

    /** Creates a [Schedule] that outputs the [Input] unmodified. */
    public fun <Input> identity(): Schedule<Input, Input> {
      fun loop(input: Input): Decision<Input, Input> =
        Continue(input, ZERO) { loop(it) }

      return Schedule { loop(it) }
    }

    /** Creates a [spaced] backing-off [Schedule] with the provided [duration]. */
    public fun <Input> spaced(duration: Duration): Schedule<Input, Long> {
      fun loop(input: Long): Decision<Input, Long> = Continue(input, duration) { loop(input + 1) }
      return Schedule { loop(0L) }
    }

    /** Creates a [fibonacci] backing-off [Schedule] with the provided [one]. */
    public fun <Input> fibonacci(one: Duration): Schedule<Input, Duration> {
      fun loop(prev: Duration, curr: Duration): Decision<Input, Duration> =
        (prev + curr).let { next ->
          Continue(next, next) { loop(curr, next) }
        }

      return Schedule { loop(0.nanoseconds, one) }
    }

    /** Creates a linear backing-off [Schedule] with the provided [base] value. */
    public fun <Input> linear(base: Duration): Schedule<Input, Duration> {
      fun loop(count: Int): Decision<Input, Duration> =
        (base * count).let { next ->
          Continue(next, next) { loop(count + 1) }
        }

      return Schedule { loop(1) }
    }

    /** Creates a [exponential] backing-off [Schedule] with the provided [base] duration and exponential [factor]. */
    public fun <Input> exponential(base: Duration, factor: Double = 2.0): Schedule<Input, Duration> {
      fun loop(count: Int): Decision<Input, Duration> =
        (base * factor.pow(count)).let { next ->
          Continue(next, next) { loop(count + 1) }
        }

      return Schedule { loop(0) }
    }

    /** Creates a [Schedule] which [collect]s all its [Input] in a [List]. */
    public fun <Input> collect(): Schedule<Input, List<Input>> =
      identity<Input>().collect()

    /** Creates a Schedule that recurs [n] times. */
    public fun <Input> recurs(n: Int): Schedule<Input, Long> =
      recurs(n.toLong())

    /** Creates a Schedule that recurs [n] times. */
    public fun <Input> recurs(n: Long): Schedule<Input, Long> {
      fun loop(input: Long): Decision<Input, Long> =
        if (input < n) Continue(input, ZERO) { loop(input + 1) } else Done(input)

      return Schedule { loop(0L) }
    }

    /** Creates a [Schedule] that runs [forever] */
    public fun <Input> forever(): Schedule<Input, Long> =
      unfold(0) { it + 1 }

    /**
     * Creates a [Schedule] that unfolds values of [Output] with an [initial] value, and the [next] function to compute the next value.
     */
    public fun <Input, Output> unfold(initial: Output, next: suspend (Output) -> Output): Schedule<Input, Output> {
      fun loop(input: Output): Decision<Input, Output> =
        Continue(input, ZERO) { loop(next(input)) }

      return Schedule { loop(initial) }
    }
  }

  public sealed interface Decision<in Input, out Output> {
    public val output: Output

    public data class Done<Output>(override val output: Output) : Decision<Any?, Output>
    public data class Continue<in Input, out Output>(
      override val output: Output,
      val delay: Duration,
      val next: Next<Input, Output>
    ) : Decision<Input, Output>
  }
}

/**
 * Retries [action] using any [Throwable] that occurred as the input to the [Schedule].
 * It will throw the last exception if the [Schedule] is exhausted, and ignores the output of the [Schedule].
 */
public suspend fun <Input> Schedule<Throwable, *>.retry(action: suspend () -> Input): Input =
  retryOrElse(action) { e, _ -> throw e }

/**
 * Retries [action] using any [Throwable] that occurred as the input to the [Schedule].
 * If the [Schedule] is exhausted,
 * it will invoke [orElse] with the last exception and the output of the [Schedule] to produce a fallback [Input] value.
 */
public suspend fun <Input, Output> Schedule<Throwable, Output>.retryOrElse(
  action: suspend () -> Input,
  orElse: suspend (Throwable, Output) -> Input
): Input = retryOrElseEither(action, orElse).merge()

/**
 * Retries [action] using any [Throwable] that occurred as the input to the [Schedule].
 * If the [Schedule] is exhausted,
 * it will invoke [orElse] with the last exception and the output of the [Schedule] to produce a fallback value of [A].
 * Returns [Either] with the fallback value if the [Schedule] is exhausted, or the successful result of [action].
 */
public suspend fun <Input, Output, A> Schedule<Throwable, Output>.retryOrElseEither(
  action: suspend () -> Input,
  orElse: suspend (Throwable, Output) -> A
): Either<A, Input> {
  var step: Next<Throwable, Output> = step

  while (true) {
    currentCoroutineContext().ensureActive()
    try {
      return Either.Right(action.invoke())
    } catch (e: Throwable) {
      when (val decision = step(e)) {
        is Continue -> {
          if (decision.delay != ZERO) delay(decision.delay)
          step = decision.next
        }

        is Done -> return Either.Left(orElse(e.nonFatalOrThrow(), decision.output))
      }
    }
  }
}
