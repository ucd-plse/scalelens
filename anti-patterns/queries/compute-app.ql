import java
import critical_path
import @DCFLIB@

predicate reaches(Callable call_from, Callable call_to) {
  call_from = call_to
  or
  exists(Callable intermediate |
    call_from.getACallee() = intermediate and
    reaches(intermediate, call_to)
  )
}

predicate couldCall(Stmt stmt, string fullyQualifiedName) {
  exists(Call call, Callable call_from, Callable call_to |
    call.getEnclosingStmt() = stmt and
    call_from = call.getCallee() and
    call_to.getQualifiedName() = fullyQualifiedName and
    reaches(call_from, call_to)
  )
}

from string dimensionalCodeFragment, Stmt statement
where
  isDimensional(dimensionalCodeFragment) and
  couldCall(statement, dimensionalCodeFragment) and
  isCritical(statement.getEnclosingCallable().getQualifiedName())
select dimensionalCodeFragment, "compute-app" as pattern
