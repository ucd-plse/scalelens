import java
import @DCFLIB@

predicate isWithinLockBlock(Stmt stmt) {
  exists(Call lockCall, Call unlockCall, TryStmt tryStmt |
    (
      lockCall.getCallee().getName() = "lock" or
      lockCall.getCallee().getName() = "tryLock" or
      lockCall.getCallee().getName() = "writeLock"
    ) and
    // lockHasCompetitor(lockCall) and
    (
      unlockCall.getCallee().getName() = "unlock" or
      unlockCall.getCallee().getName() = "writeUnlock"
    ) and
    unlockCall.getEnclosingStmt() = tryStmt.getFinally().getAStmt() and
    stmt.getEnclosingStmt*() = tryStmt
  )
}

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
    //(reaches(call_from, call_to) or reaches(call_to, call_from))
    reaches(call_from, call_to)
  )
}

predicate holdsImmediateLock(string fullyQualifiedName) {
  exists(Method method |
    method.getQualifiedName() = fullyQualifiedName and
    exists(Call lockCall |
      lockCall.getCallee().getName() = "lock" and
      lockCall.getEnclosingStmt().getEnclosingCallable() = method
    )
  )
  or
  exists(Method method, Method subMethod |
    method.getQualifiedName() = fullyQualifiedName and
    method.getACallee() = subMethod and
    exists(Call lockCall |
      lockCall.getCallee().getName() = "lock" and
      lockCall.getEnclosingStmt().getEnclosingCallable() = subMethod
    )
  )
}

from string dimensionalCodeFragment, Stmt statement
where
  isDimensional(dimensionalCodeFragment) and
  (
    couldCall(statement, dimensionalCodeFragment) and
    isWithinLockBlock(statement)
    or
    holdsImmediateLock(dimensionalCodeFragment)
  )
select dimensionalCodeFragment, "compute-sync" as pattern
