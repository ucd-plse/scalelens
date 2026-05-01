import java
import @DCFLIB@

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

from string dimensionalCodeFragment
where
  isDimensional(dimensionalCodeFragment) and
  hasUnboundedTemporaryAllocation(dimensionalCodeFragment)
select dimensionalCodeFragment, "unbound-allocation" as pattern
