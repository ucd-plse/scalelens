import java
import io
import @DCFLIB@

predicate reaches(Callable call_from, Callable call_to) {
  call_from = call_to
  or
  exists(Callable intermediate |
    call_from.getACallee() = intermediate and
    reaches(intermediate, call_to)
  )
}

predicate couldCall(string from_FQN, string to_FQN) {
  exists(Call call, Callable call_from, Callable call_to |
    call_from.getQualifiedName() = from_FQN and
    call_to.getQualifiedName() = to_FQN and
    reaches(call_from, call_to)
  )
}

from string dimensionalCodeFragment
where
  isDimensional(dimensionalCodeFragment) and
  hasImmediateIO(dimensionalCodeFragment)
select dimensionalCodeFragment, "compute-cross" as pattern
