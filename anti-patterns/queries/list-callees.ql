import java
import dimensional_CA_3_11_0

predicate reaches(Callable call_from, Callable call_to) {
  call_from = call_to
  or
  exists(Callable intermediate |
    call_from.getACallee() = intermediate and
    reaches(intermediate, call_to)
  )
}

from Callable caller, Callable callee
where
  caller.getQualifiedName() = "@DCF@" and
  isDimensional(callee.getQualifiedName()) and
  reaches(caller, callee)
select callee.getQualifiedName()
