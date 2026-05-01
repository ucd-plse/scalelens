import java
import dimensional

predicate temporaryAllocation(Type type) {
  type.hasName("Set") or
  type.hasName("List") or
  type.hasName("Map") or
  type.hasName("Queue") or
  type.hasName("Deque")
}

predicate hasUnboundedTemporaryAllocation(string fullyQualifiedName) {
  exists(LocalVariableDeclStmt declStmt |
    declStmt.getEnclosingCallable().getQualifiedName() = fullyQualifiedName and
    temporaryAllocation(declStmt.getAVariable().getVariable().getType().getErasure()) and
    exists(MethodCall methodCall |
      methodCall.getMethod().hasName("add") and
      methodCall.getQualifier().(VarAccess).getVariable() = declStmt.getAVariable().getVariable()
    )
  )
}

predicate hasNewAllocation(string fullyQualifiedName) {
  exists(Method method, NewClassExpr newExpr |
    method.getQualifiedName() = fullyQualifiedName and
    newExpr.getEnclosingCallable() = method
  )
}

from string dimensionalCodeFragment
where
  isDimensional(dimensionalCodeFragment) and
  (hasUnboundedTemporaryAllocation(dimensionalCodeFragment) or
  hasNewAllocation(dimensionalCodeFragment))
select dimensionalCodeFragment, "unbound-allocation" as pattern
