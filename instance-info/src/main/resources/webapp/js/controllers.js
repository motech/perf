(function () {
    'use strict';

    var controllers = angular.module('instance-info.controllers', []);

    /*
     *
     * Info
     *
     */
    controllers.controller('InfoController', function ($scope, $http, $timeout) {
        $scope.name = "...";

        innerLayout({
            spacing_closed: 30,
            east__minSize: 200,
            east__maxSize: 350
        });

        $http.get('../instance-info/name')
            .success(function(response){
                $scope.name = response;
            })
            .error(function(response) {
                $scope.errors.push($scope.msg('instance-info.web.info.noName', response));
            });
    });

}());