FLOW.MonitoringDataTableView = FLOW.View.extend({
  showingDetailsDialog: false,
  cursorStart: null,

  pageNumber: function(){
	return FLOW.router.surveyedLocaleController.get('pageNumber');
  }.property('FLOW.router.surveyedLocaleController.pageNumber'),

  showDetailsDialog: function (evt) {
	FLOW.surveyInstanceControl.set('content', FLOW.store.findQuery(FLOW.SurveyInstance, {
		surveyedLocaleId: evt.context.get('keyId')
	}));
    this.toggleProperty('showingDetailsDialog');
  },

  showApprovalStatusColumn: function () {
      return FLOW.Env.enableDataApproval;
  }.property(),

  closeDetailsDialog: function () {
    this.toggleProperty('showingDetailsDialog');
  },

  showSurveyInstanceDetails: function (evt) {
    FLOW.questionAnswerControl.doQuestionAnswerQuery(evt.context.get('keyId'));
    $('.si_details').hide();
    $('tr[data-flow-id="si_details_' + evt.context.get('keyId') + '"]').show();
  },

  showSurveyedLocaleDeleteButton: function() {
    return FLOW.surveyedLocaleControl.get('userCanDelete');
  }.property(),

  findSurveyedLocale: function (evt) {
	  var ident = this.get('identifier'),
	      displayName = this.get('displayName'),
	      sgId = FLOW.selectedControl.get('selectedSurveyGroup'),
	      cursorType = FLOW.metaControl.get('cursorType');
        criteria = {};

	  if (ident) {
		  criteria.identifier = ident;
	  }

	  if (displayName) {
		  criteria.displayName = displayName;
	  }

	  if (sgId) {
		  criteria.surveyGroupId = sgId.get('keyId');
	  }

	  if (this.get('cursorStart')) {
		criteria.since = this.get('cursorStart');
	  }
      FLOW.router.surveyedLocaleController.populate(criteria);
  },

  doNextPage: function () {
	var cursorArray, cursorStart;
	cursorArray = FLOW.router.surveyedLocaleController.get('sinceArray');
	cursorStart = cursorArray.length > 0 ? cursorArray[cursorArray.length - 1] : null;
	this.set('cursorStart', cursorStart);
    this.findSurveyedLocale();
    FLOW.router.surveyedLocaleController.set('pageNumber', FLOW.router.surveyedLocaleController.get('pageNumber') + 1);
  },

  doPrevPage: function () {
	var cursorArray, cursorStart;
	cursorArray = FLOW.router.surveyedLocaleController.get('sinceArray');
	cursorStart = cursorArray.length - 3 > -1 ? cursorArray[cursorArray.length - 3] : null;
	this.set('cursorStart', cursorStart);
    this.findSurveyedLocale();
    FLOW.router.surveyedLocaleController.set('pageNumber', FLOW.router.surveyedLocaleController.get('pageNumber') - 1);
  },

  hasNextPage: function () {
    return FLOW.metaControl.get('numSLLoaded') == 20;
  }.property('FLOW.metaControl.numSLLoaded'),

  hasPrevPage: function () {
    return FLOW.router.surveyedLocaleController.get('pageNumber');
  }.property('FLOW.router.surveyedLocaleController.pageNumber'),
});

FLOW.DataPointApprovalStatusView = FLOW.View.extend({
    content: null,

    latestApprovalStepTitle: function () {
        var stepsController = FLOW.router.get('approvalStepsController');
        if(stepsController.get('firstObject')) {
            return stepsController.get('firstObject').get('title');
        }
    }.property(),
});

FLOW.DataPointApprovalView = FLOW.View.extend({
    approvalStatus: Ember.A([{ label: Ember.String.loc('_approved') },{ label: Ember.String.loc('_rejected')}])
});