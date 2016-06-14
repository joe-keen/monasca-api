/*
 * (C) Copyright 2016 Hewlett Packard Enterprise Development Company LP
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package monasca.api.app.command;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

import monasca.api.app.validation.NotificationMethodValidation;
import monasca.api.domain.model.notificationmethod.NotificationMethodType;

public class UpdateNotificationMethodCommand {
    @NotEmpty
    @Size(min = 1, max = 250)
    public String name;
    @NotNull
    public NotificationMethodType type;
    @NotEmpty
    @Size(min = 1, max = 512)
    public String address;
    @NotNull
    public String period;

    public UpdateNotificationMethodCommand() {}

    public UpdateNotificationMethodCommand(String name, NotificationMethodType type, String address, String period) {
        this.name = name;
        this.type = type;
        this.address = address;
        this.period = period;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        UpdateNotificationMethodCommand other = (UpdateNotificationMethodCommand) obj;
        if (address == null) {
            if (other.address != null)
                return false;
        } else if (!address.equals(other.address))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (period == null) {
            if (other.period != null)
                return false;
        } else if (!period.equals(other.period))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

    public void validate(List<Integer> validPeriods) {
        NotificationMethodValidation.validate(type, address, period, validPeriods);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((address == null) ? 0 : address.hashCode());
        result = prime * result + ((period == null) ? 0 : period.hashCode());
        return result;
    }
}