##########################GO-LICENSE-START################################
# Copyright 2014 ThoughtWorks, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
##########################GO-LICENSE-END##################################

module Admin
  class PipelineConfigsController < ::ApplicationController
    include ApiV1::AuthenticationHelper

    layout 'pipeline_configs'
    before_action :check_admin_user_and_401
    before_action :load_pipeline

    def edit
      @all_resources = go_config_service.getAllResources().map(&:getName)
      @all_users     = user_service.allUsernames()
      @all_roles     = user_service.allRoleNames()
      @view_title    = "Edit Pipeline - #{params[:pipeline_name]}"
    end

    private

    def load_pipeline
      @pipeline_config = pipeline_config_service.getPipelineConfig(params[:pipeline_name])
      raise ApiV1::RecordNotFound if @pipeline_config.nil?
    end

    def plugin_templates
      store = com.thoughtworks.go.plugin.access.pluggabletask.PluggableTaskConfigStore.store()
      store.pluginIds().inject([]) do |memo, plugin_id|
        preference = store.preferenceFor(plugin_id)
        memo << {
          plugin_id:     plugin_id,
          version:       default_plugin_manager.getPluginDescriptorFor(plugin_id).version,
          name:          preference.getView().displayValue,
          template:      preference.getView().template,
          configuration: PluggableTaskConfigStore.store().preferenceFor(plugin_id).getConfig().list().map do |property|
            {
              name:  property.getKey(),
              value: property.getValue()
            }
          end

        }
      end
    end

    helper_method :plugin_templates

  end

end
