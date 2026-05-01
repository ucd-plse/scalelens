import java

predicate reaches(Callable call_from, Callable call_to) {
  call_from = call_to
  or
  exists(Callable intermediate |
    call_from.getACallee() = intermediate and
    reaches(intermediate, call_to)
  )
}

from Callable dimensionalCodeFragment, Callable neighbor
where
  dimensionalCodeFragment.getQualifiedName() = "@DCF@" and
  (
    reaches(dimensionalCodeFragment, neighbor) or
    reaches(neighbor, dimensionalCodeFragment)
  )
select neighbor.getQualifiedName(), dimensionalCodeFragment.getQualifiedName()
