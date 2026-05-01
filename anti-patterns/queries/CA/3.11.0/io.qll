import java

predicate throwsIOException(string fullyQualifiedName) {
  exists(Callable m |
    m.getQualifiedName() = fullyQualifiedName and
    m.getAnException().getName() = "IOException"
  )
}

predicate hasImmediateIO(string fullyQualifiedName) {
  exists(Method method |
    method.getQualifiedName() = fullyQualifiedName and
    exists(Call ioCall |
      (
        ioCall.getCallee().getAnException().getName() = "IOException" or
        ioCall.getCallee().getQualifiedName() = "getWriter"
      ) and
      ioCall.getEnclosingStmt().getEnclosingCallable() = method
    )
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

predicate couldCall(string from_FQN, string to_FQN) {
  exists(Call call, Callable call_from, Callable call_to |
    call_from.getQualifiedName() = from_FQN and
    call_to.getQualifiedName() = to_FQN and
    reaches(call_from, call_to)
  )
}

predicate hasNonImmediateIO(string fullyQualifiedName) {
  exists(Method caller, Method callee |
    caller.getQualifiedName() = fullyQualifiedName and
    couldCall(caller.getQualifiedName(), callee.getQualifiedName()) and
    not hasImmediateIO(callee.getQualifiedName())
  )
}
