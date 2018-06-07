/* globals $ */

export function calendar() {

  // Event sources
  var sources = {
    month: {
      url: "/calendar/month/events",
      type: "GET",
      error: handleEventSourceError
    },
    agendaWeek: {
      url: "/calendar/week/events",
      type: "GET",
      error: handleEventSourceError
    },
    listWeek: {
      url: "/calendar/list/events",
      type: "GET",
      error: handleEventSourceError
    },
  };
  // Initialize calendar
  var calendar = $("#calendar");
  calendar.fullCalendar({
    schedulerLicenseKey: "CC-Attribution-NonCommercial-NoDerivatives",
    eventSources: [
      sources.month,
      sources.agendaWeek,
      sources.listWeek
    ],
    header: {
      left: "month,agendaWeek,listWeek",
      center: "title"
    },
    buttonText: {
      month: "Month",
      agendaWeek: "Week",
      listWeek: "List",
      today: "Today"
    },
    nowIndicator: true,
    businessHours: {
      dow: [1, 2, 3, 4, 5, 6],
      start: "08:00",
      end: "18:00"
    },
    minTime: "08:00:00",
    maxTime: "18:00:00",
    contentHeight: "auto",
    eventLimit: true,
    timeFormat: "hh:mmA",
    views: {
      agendaWeek: {
        columnHeaderFormat: "ddd, D/M",
        slotLabelFormat: "hh:mmA",
        allDayText: "All Day"
      },
      listWeek: {
        listDayAltFormat: "MMM D, YYYY",
        noEventsMessage: "Nothing to display"
      }
    },
    slotEventOverlap: false,
    viewRender: handleViewRender,
    eventRender: handleEventRender
  });

  function handleEventSourceError() {
    console.log("Failed to fetch calendar events");
  }

  var lastView = "month";
  function handleViewRender(view) {
    if (view.name != lastView) {
      // Change event source
      calendar.fullCalendar("removeEventSources");
      calendar.fullCalendar("addEventSource", sources[view.name]);
      lastView = view.name;
    }
  }

  function handleEventRender(event, element) {
    // Enable event modal
    element.attr("data-toggle", "modal");
    element.attr("data-target", "#event-modal");
  }

}
