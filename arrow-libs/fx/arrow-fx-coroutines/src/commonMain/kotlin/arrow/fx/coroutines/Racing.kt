package arrow.fx.coroutines

import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.atomic.value
import arrow.core.identity
import arrow.core.nonFatalOrThrow
import arrow.core.prependTo
import arrow.core.raise.DelicateRaiseApi
import arrow.core.raise.RaiseCancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.select
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A DSL that allows racing many `suspend` functions in parallel against each-other,
 * it yields a final result of [A] based on the first function that yields a result.
 * A racer can yield a result based on [RacingScope.race], or [RacingScope.raceOrFail].
 *
 * [RacingScope.race] will call [handleException] in case of an exception,
 * and then await **another successful result** but not cancel the race. Whilst [RacingScope.raceOrFail] will cancel the race,
 * and rethrow the exception that occurred and thus cancel the race and all participating racers.
 *
 * ```kotlin
 * suspend fun winner(): String = racing {
 *   race { delay(1000); "Winner" }
 *   race { throw RuntimeException("Loser") }
 * } // Winner (logged RuntimeException)
 *
 * suspend fun winner(): String = racing {
 *   race { delay(1000); "loser" }
 *   raceOrFail { throw RuntimeException("Loser") }
 * } // RuntimeException
 * ```
 *
 * **Important:** a racing program with no racers will hang forever.
 * ```kotlin
 * suspend fun never(): Nothing = racing { }
 * ```
 *
 * @param handleException handle any exception that occurred in [RacingScope.race],
 * by default it [Throwable.printStackTrace].
 * @param block the body of the DSL that describes the racing logic
 * @return the winning value of [A].
 */
public suspend fun <A> racing(
  handleException: ((context: CoroutineContext, exception: Throwable) -> Unit)? = null,
  block: RacingScope<A>.() -> Unit,
): A = coroutineScope {
  val exceptionHandler = handleException ?: defaultExceptionHandler()::handleException
  select {
    val scope = SelectRacingScope(this@select, this@coroutineScope, exceptionHandler)
    block(scope)
    // TODO add this check??
    require(scope.racers.value.isNotEmpty()) { "A racing program with no racers can never yield a result." }
  }
}

public interface RacingScope<A> {
  public suspend fun race(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> A
  )

  public suspend fun raceOrFail(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> A
  )
}

private class SelectRacingScope<A>(
  private val select: SelectBuilder<A>,
  private val scope: CoroutineScope,
  private val handleException: (context: CoroutineContext, exception: Throwable) -> Unit
) : RacingScope<A>, CoroutineScope by scope {
  val racers: Atomic<List<Deferred<A>>> = Atomic(emptyList())

  override suspend fun raceOrFail(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> A
  ) {
    /* First we create a lazy racer,
     * and we add it in front of the existing racers such that we maintain correct order.
     * After we've successfully registered the racer, we check for race conditions,
     * and 'start' racing.
     */
    val racer = scope.async(
      start = CoroutineStart.LAZY,
      context = context,
      block = block
    )
    racers.update { racer prependTo it }
    if (isActive) {
      require(racer.start()) { "Racer not started" }
      return with(select) {
        racer.onAwait.invoke(::identity)
      }
    }
  }

  // To not fail the entire race, we allow users to handle the exceptions but do not cancel the race.
  @OptIn(DelicateRaiseApi::class)
  override suspend fun race(context: CoroutineContext, block: suspend CoroutineScope.() -> A) =
    raceOrFail {
      try {
        block()
      } catch (e: RaiseCancellationException) {
        // `Raise<E>` error is ignored... Can we do better here?
        awaitCancellation()
      } catch (e: Throwable) {
        handleException(currentCoroutineContext(), e.nonFatalOrThrow())
        awaitCancellation()
      }
    }
}

private suspend fun defaultExceptionHandler(): CoroutineExceptionHandler =
  currentCoroutineContext()[CoroutineExceptionHandler] ?: DefaultCoroutineExceptionHandler

private object DefaultCoroutineExceptionHandler : CoroutineExceptionHandler {
  override val key: CoroutineContext.Key<CoroutineExceptionHandler> = CoroutineExceptionHandler

  override fun handleException(context: CoroutineContext, exception: Throwable) {
    if (exception !is CancellationException) exception.printStackTrace()
  }
}
