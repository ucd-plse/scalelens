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
      ioCall.getCallee().getAnException().getName() = "IOException" and
      ioCall.getEnclosingStmt().getEnclosingCallable() = method
    )
  )
}
