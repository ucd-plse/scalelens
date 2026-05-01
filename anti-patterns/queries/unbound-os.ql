import java
import semmle.code.java.security.Encryption
import @DCFLIB@

predicate hasOSCall(string fullyQualifiedName) {
  exists(CreateSocket socket |
    socket.getEnclosingCallable().getQualifiedName() = fullyQualifiedName
  )
}

from string dimensionalCodeFragment
where
  isDimensional(dimensionalCodeFragment) and
  hasOSCall(dimensionalCodeFragment)
select dimensionalCodeFragment, "unbound-os" as pattern
