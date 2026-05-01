import java
import semmle.code.java.Collections
import @DCFLIB@

predicate hasAdditionsToCollection(string fullyQualifiedName) {
  exists(Method method |
    method.getQualifiedName() = fullyQualifiedName and
    exists(Call addCall |
      (
        addCall.getCallee().getName() = "add" or
        addCall.getCallee().getName() = "append"
      ) and
      addCall.getEnclosingStmt().getEnclosingCallable() = method
    )
  )
}

from string fullyQualifiedName
where
  isDimensional(fullyQualifiedName) and
  hasAdditionsToCollection(fullyQualifiedName)
select fullyQualifiedName, "unbound-collection" as pattern
