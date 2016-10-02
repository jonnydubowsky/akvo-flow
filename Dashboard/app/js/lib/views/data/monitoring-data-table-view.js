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

  findSurveyedLocale: function (evt) {
	  var ident = this.get('identifier'),
	      displayName = this.get('displayName'),
	      sgId = FLOW.selectedControl.get('selectedSurveyGroup'),
	      cursorType = FLOW.metaControl.get('cursorType'),
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

/**
 * View of each row/data point in the monitoring data tab
 */
FLOW.DataPointView = FLOW.View.extend({
    templateName: 'navData/monitoring-data-row',

    approvalStatus: [{label: Ember.String.loc('_pending'), value: 'pending'}, { label: Ember.String.loc('_approved'), value: 'approved' },{ label: Ember.String.loc('_rejected'), value: 'rejected'}],

    showDataApprovalBlock: false,

    showSurveyedLocaleDeleteButton: function() {
        return FLOW.router.surveyedLocaleController.get('userCanDelete');
    }.property(),

    showApprovalStatusColumn: function () {
        return this.get('parentView').get('showApprovalStatusColumn');
    }.property(),

    loadDataPointApprovalObserver: function () {
        if(!this.get('showDataApprovalBlock')) {
            return; // do nothing when hiding approval block
        }

        var dataPoint = this.get('content');
        if (dataPoint) {
            FLOW.router.dataPointApprovalController.loadBySurveyedLocaleId(dataPoint.get('keyId'));
        }
    }.observes('this.showDataApprovalBlock'),

    toggleShowDataApprovalBlock: function () {
        this.toggleProperty('showDataApprovalBlock');
    },
});

/**
 * View to render the status of a data point in the approval
 * status cell of each data point / row
 */
FLOW.DataPointApprovalStatusView = FLOW.View.extend({
    content: null,

    latestApprovalStepTitle: function () {
        var stepsController = FLOW.router.get('approvalStepsController');
        if(stepsController.get('firstObject')) {
            return stepsController.get('firstObject').get('title');
        }
    }.property(),
});

/**
 * The data approval view for each approval step of a data point
 */
FLOW.DataPointApprovalView = FLOW.View.extend({
    step: null,

    dataPoint: null,

    status: null,

    comment: null,

    dataPointApproval: function () {
        var approvals = FLOW.router.dataPointApprovalController.get('content');
        if(!approvals) {
            return;
        }

        var stepId = this.step && this.step.get('keyId');
        var surveyedLocaleId = this.dataPoint && this.dataPoint.get('keyId');
        var filteredApprovalList = approvals.filter(function (item){
            return (item.get('surveyedLocaleId') === surveyedLocaleId &&
                    item.get('approvalStepId') === stepId);
        })
        return filteredApprovalList.get('firstObject');
    }.property('FLOW.router.dataPointApprovalController.content.@each'),

    isApprovedStep: function () {
        var dataPointApproval = this.get('dataPointApproval');
        return dataPointApproval;
    }.property('this.dataPointApproval'),

    /*
     *  Submit data approval properties to controller
     */
    submitDataPointApproval: function (event) {

        var dataPointApproval = Ember.Object.create({
            surveyedLocaleId: this.get('dataPoint').get('keyId'),
            approvalStepId: this.get('step').get('keyId'),
            approverUserName: null, // explicitly left empty to be set on server-side.
            status: this.get('status').toUpperCase(),
            comment: this.get('comment'),
        });

        FLOW.router.dataPointApprovalController.add(dataPointApproval);
    },
});