package com.courier.tracker;

import java.util.*;

class Workflow {
  private static final Map<Status, List<Status>> TRANSITIONS =
      Map.of(
          Status.REGISTERED, List.of(Status.PICKED_UP, Status.CANCELLED),
          Status.PICKED_UP, List.of(Status.IN_TRANSIT, Status.RETURNED),
          Status.IN_TRANSIT, List.of(Status.OUT_FOR_DELIVERY, Status.RETURNED),
          Status.OUT_FOR_DELIVERY, List.of(Status.DELIVERED, Status.DELIVERY_FAILED),
          Status.DELIVERY_FAILED, List.of(Status.OUT_FOR_DELIVERY, Status.RETURNED));

  static List<Status> next(Status s) {
    return TRANSITIONS.getOrDefault(s, List.of());
  }

  static boolean terminal(Status s) {
    return next(s).isEmpty();
  }

  static void validate(Status from, Status to) {
    if (!next(from).contains(to))
      throw new ApiException(409, "Cannot move from " + from + " to " + to + ".");
  }
}
