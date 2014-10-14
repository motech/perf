(function () {
    'use strict';

    angular.module('instance-info', ['motech-dashboard', 'instance-info.controllers', 'ngCookies', 'ui.bootstrap']).config(
    ['$routeProvider',
        function ($routeProvider) {
            $routeProvider.
                when('/instance-info/info', {templateUrl: '../instance-info/resources/partials/info.html', controller: 'InfoController'});
    }]);
}());
